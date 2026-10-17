package vn.edu.multigame.achievements.service;

import java.util.*;
import jakarta.persistence.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import vn.edu.multigame.achievements.model.*;
import vn.edu.multigame.achievements.repository.AwardRepository;
import vn.edu.multigame.achievements.repository.StageRankEvidenceRepository;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.repository.GameSessionRepository;
import vn.edu.multigame.game.service.GameAccessPolicy;
import vn.edu.multigame.room.repository.RoomRepository;

/** Room -> Game -> decision -> sorted original Player Users. No network or queue waiting in this TX. */
@Service @Profile("mysql") @Transactional(isolation=Isolation.READ_COMMITTED,timeout=5)
public class AwardTransactions {
    private final RoomRepository rooms;private final GameSessionRepository games;private final AwardRepository awards;
    private final AccountAccessPolicy accounts;private final GameAccessPolicy restrictions;private final JdbcTemplate jdbc;private final AwardClock clock;private final StageRankEvidenceRepository stageEvidence;
    @PersistenceContext EntityManager entities;
    public AwardTransactions(RoomRepository rooms,GameSessionRepository games,AwardRepository awards,AccountAccessPolicy accounts,GameAccessPolicy restrictions,JdbcTemplate jdbc,AwardClock clock,StageRankEvidenceRepository stageEvidence){
        this.rooms=rooms;this.games=games;this.awards=awards;this.accounts=accounts;this.restrictions=restrictions;this.jdbc=jdbc;this.clock=clock;
        this.stageEvidence=stageEvidence;
    }
    public void seedMissing(long id){
        var game=games.findById(id).orElseThrow();rooms.findLockedById(game.getRoomId()).orElseThrow();entities.refresh(game,LockModeType.PESSIMISTIC_WRITE);
        if(game.getStatus()==GameStatus.FINISHED && game.getAwardPolicySnapshot()!=null)awards.seed(game,clock.now());
    }
    public void finalizeGame(long id){
        var game=games.findById(id).orElseThrow();rooms.findLockedById(game.getRoomId()).orElseThrow();entities.refresh(game,LockModeType.PESSIMISTIC_WRITE);
        if(game.getStatus()!=GameStatus.FINISHED || game.getAwardPolicySnapshot()==null)return;
        awards.seed(game,clock.now());if(awards.terminal(game))return;
        var policy=game.getAwardPolicySnapshot();policy.requireSupported();
        if(game.getSchemaVersion()!=2 || game.getRulesVersion()!=2 || !game.getGameType().equals("NORMAL"))throw new IllegalStateException("AWARD_SCOPE_MISMATCH");
        var roster=awards.players(id);var states=new LinkedHashMap<Long,AccountAccessPolicy.State>();
        for(var p:roster)states.put(p.userId(),accounts.lock(p.userId()));
        long evaluated=clock.now();var removed=restrictions.restrictedUsers(id);var metrics=awards.metrics(id);var counts=awards.counts(id);
        boolean completed=game.getEndReason()==EndReason.COMPLETED;
        if(completed)awards.requireComplete(id,game.getQuestionCount(),roster,metrics,counts);
        var inputs=roster.stream().map(p->{var s=states.get(p.userId());return new AwardEvaluator.Player(p.userId(),p.score(),p.correctTime(),p.finalRank(),
                !removed.contains(p.userId()) && s.deletedAtMs()==null && !s.banned(evaluated),metrics.getOrDefault(p.userId(),List.of()));}).toList();
        var result=new AwardEvaluator().evaluate(policy,game.getQuestionCount(),counts.scored(),completed,inputs);
        var committedAwards=new ArrayList<>(result.awards());
        if(result.qualified() && policy.advanced()!=null){
            var stage=stageEvidence.read(id,inputs.stream().filter(AwardEvaluator.Player::eligible).map(AwardEvaluator.Player::userId).toList());
            committedAwards.addAll(new AdvancedAwardEvaluator().evaluate(policy,result,inputs,stage));
        }
        for(var p:inputs){
            var s=states.get(p.userId());var reasons=new ArrayList<String>();
            if(removed.contains(p.userId()))reasons.add("ADMIN_GAME_RESTRICTION");if(s.deletedAtMs()!=null)reasons.add("ACCOUNT_UNAVAILABLE");if(s.banned(evaluated))reasons.add("ACCOUNT_RESTRICTED");
            var targetEvidence=jdbc.queryForList("select action_id,ban_version,state from account_effect_intent where game_session_id=? and user_id=? and effect_type='GAME' order by id",id,p.userId());
            var kickEvidence=jdbc.queryForList("select action_id from game_participation_restriction where game_session_id=? and user_id=?",id,p.userId());
            var evidence=Map.of("reasonCodes",reasons,"banVersion",s.version(),"banKind",s.kind(),"gameIntents",targetEvidence,"kickMarkers",kickEvidence);
            int gameDelta=result.qualified() && p.eligible()?1:0;
            int win=result.awards().stream().anyMatch(a->a.userId()==p.userId() && a.type().equals("CUP"))?1:0;
            jdbc.update("insert into game_award_participant(game_session_id,user_id,eligible,private_evidence,history_rank,award_rank,official_game_delta,official_win_delta,participated_count,correct_count,correct_time_ms,mode_metrics) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,p.userId(),p.eligible(),awards.encode(evidence),p.historyRank(),result.ranks().get(p.userId()),gameDelta,win,p.participated(),p.correct(),p.correctTimeMs(),awards.encode(p.modes()));
        }
        long decided=clock.now();
        for(var a:committedAwards)jdbc.update("insert into achievement_award(game_session_id,user_id,award_type,mode_key,earned_at_ms,evidence_snapshot) values(?,?,?,?,?,?)",id,a.userId(),a.type(),a.modeKey(),decided,a.evidence().isEmpty()?null:awards.encode(a.evidence()));
        if(jdbc.update("update game_award_decision set state=?,revision=1,eligibility_evaluated_at_ms=?,decided_at_ms=?,reason_codes=?,initial_player_count=?,eligible_player_count=?,configured_count=?,opened_count=?,scored_count=?,eligible_correct_count=?,last_error_code=null where game_session_id=? and state='PENDING' and revision=0",
                result.qualified()?"DECIDED":"NOT_QUALIFIED",evaluated,decided,awards.encode(result.reasons()),roster.size(),result.eligibleCount(),game.getQuestionCount(),counts.opened(),counts.scored(),result.eligibleCorrect(),id)!=1)throw new IllegalStateException("AWARD_CAS_FAILED");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void beforeCommit(boolean readOnly){long now=clock.now();
                if(states.values().stream().anyMatch(s->s.banned(evaluated)!=s.banned(now)))throw new org.springframework.dao.TransientDataAccessResourceException("AWARD_EXPIRY_BOUNDARY");}
        });
    }
}
