package vn.edu.multigame.game.service;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.engine.RankingEngine;
import vn.edu.multigame.game.entity.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.repository.*;
import vn.edu.multigame.quiz.enums.Option;

@Component @Profile("mysql")
public class GameProjection {
    private final GameMemberRepository members;
    private final PlayerSessionRepository players;
    private final GameQuestionRepository questions;
    private final AnswerRepository answers;
    private final GameStageRepository stages;
    private final vn.edu.multigame.room.service.RoomService rooms;
    public GameProjection(GameMemberRepository members,PlayerSessionRepository players,
            GameQuestionRepository questions,AnswerRepository answers,vn.edu.multigame.room.service.RoomService rooms,GameStageRepository stages) {
        this.members=members; this.players=players; this.questions=questions; this.answers=answers;
        this.rooms=rooms; this.stages=stages;
    }
    public CommittedGame copy(GameSession game) {
        boolean v2=game.getSchemaVersion()==2;
        var rows=players.findByGameSessionId(game.getId());
        var ranks=new RankingEngine().calculate(rows.stream().map(p -> new RankingEngine.Entry(p.getUserId(),p.getScore(),v2?p.getTotalCorrectAnswerTimeMs():p.getTotalAnswerTimeMs())).toList());
        Map<Long,Integer> byRank=new HashMap<>(); ranks.forEach(r -> byRank.put(r.player().userId(),r.rank()));
        Map<Long,PlayerSession> byUser=new HashMap<>(); rows.forEach(p -> byUser.put(p.getUserId(),p));
        GameQuestion q=questions.findByGameSessionIdAndOrderIndex(game.getId(),game.getCurrentQuestionIndex()).orElse(null);
        var answered=q==null?List.<Answer>of():answers.findByGameQuestionId(q.getId());
        Map<Long,Answer> byPlayer=new HashMap<>(); answered.forEach(a -> byPlayer.put(a.getPlayerSessionId(),a));
        Map<Long,GameSnapshot.Player> privatePlayers=new HashMap<>();
        for(PlayerSession p:rows) {
            Answer a=byPlayer.get(p.getId());
            privatePlayers.put(p.getUserId(),new GameSnapshot.Player(p.getUserId(),p.getPlayerState(),p.getScore(),p.getTotalAnswerTimeMs(),
                    p.getWinStreak(),p.getLoseStreak(),p.getHasMomentum(),p.getHasRecovery(),p.getRemainingSpins(),p.getStarAvailable(),
                    List.copyOf(p.getRemainingSpinPool()),p.getCurrentSpin(),p.getStarSelected(),a!=null && a.getAnswerStatus()!=AnswerStatus.NO_ANSWER,
                    a==null?null:a.getSelectedOption(),p.getEliminatedAtMs(),p.getEliminatedQuestionIndex(),v2?p.getTotalCorrectAnswerTimeMs():null,a==null?null:a.getAnswerPayload()));
        }
        var roster=members.findByGameSessionId(game.getId()).stream().sorted(Comparator.comparing(GameMember::getUserId)).map(m -> {
            var p=byUser.get(m.getUserId());
            return new GameSnapshot.Member(m.getUserId(),m.getDisplayNameSnapshot(),m.getRole(),m.getParticipation(),
                    p==null?null:p.getPlayerState(),p==null?null:p.getScore(),p==null?null:p.getTotalAnswerTimeMs(),byRank.get(m.getUserId()),v2 && p!=null?p.getTotalCorrectAnswerTimeMs():null);
        }).toList();
        GameSnapshot.Question question=null;
        if(q!=null && q.getOpenedAtMs()!=null && game.getPhase()!=Phase.DECISION && game.getPhase()!=Phase.INTRO) {
            question=new GameSnapshot.Question(q.getId(),q.getContent(),options(q),
                    imageRef(q),q.getScoredAtMs()==null?null:q.getCorrectOption(),q.getMode(),q.getStageQuestionIndex(),publicPayload(q,q.getScoredAtMs()!=null));
        }
        var results=answered.stream().filter(a -> a.getAnswerStatus()!=AnswerStatus.ACCEPTED_UNSCORED).map(a -> {
            PlayerSession p=rows.stream().filter(row -> row.getId().equals(a.getPlayerSessionId())).findFirst().orElseThrow();
            return result(a,p);
        }).sorted(Comparator.comparingLong(GameSnapshot.Result::userId)).toList();
        List<Long> winners=game.getStatus()==GameStatus.FINISHED && game.getEndReason()!=EndReason.CANCELLED && game.getEndReason()!=EndReason.SERVER_INTERRUPTED
                ?ranks.stream().filter(r -> r.rank()==1).map(r -> r.player().userId()).toList():List.of();
        var stageViews=stages.findByGameSessionIdOrderByOrderIndex(game.getId()).stream().map(s -> new GameSnapshot.Stage(s.getId(),s.getOrderIndex(),s.getMode(),s.getSourceQuizId(),s.getAuthorUserId(),s.getTitleSnapshot(),s.getFirstQuestionIndex(),s.getQuestionCount(),s.getQuestionDurationMs())).toList();
        return new CommittedGame(new GameSnapshot(game.getId(),game.getRoomId(),game.getQuizId(),game.getQuizAuthorUserId(),game.getQuizTitleSnapshot(),game.getStatus(),game.getPhase(),game.getCurrentQuestionIndex(),game.getQuestionCount(),
                game.getRevision(),0,null,null,game.getStatus()==GameStatus.FINISHED?"FINISHED":"INITIALIZING",false,game.getEndReason(),winners,
                game.getConfigSnapshot(),roster,question,results,null,game.getSchemaVersion(),game.getV2ConfigSnapshot(),stageViews,v2?game.getCurrentStageIndex():null,stageViews.stream().filter(s -> s.orderIndex()==game.getCurrentStageIndex()).findFirst().orElse(null),List.of()),privatePlayers,
                game.getStatus()==GameStatus.FINISHED?rooms.get(roster.stream().filter(m -> m.role()==MemberRole.HOST).findFirst().orElseThrow().userId(),game.getRoomId()):null);
    }
    public GameSnapshot.Result result(Answer a,PlayerSession p) {
        var effects=a.getResultSnapshot();
        return new GameSnapshot.Result(p.getUserId(),a.getAnswerStatus(),a.getBaseDelta(),a.getScoreDelta(),a.getScoreAfter(),
                a.getAnswerTimeMs(),number(effects,"totalAnswerTimeMs",p.getTotalAnswerTimeMs()).longValue(),
                number(effects,"winStreak",p.getWinStreak()).intValue(),number(effects,"loseStreak",p.getLoseStreak()).intValue(),
                flag(effects,"hasMomentumBefore") || flag(effects,"quizMomentum"),flag(effects,"hasRecoveryBefore") || flag(effects,"quizRecovery"),flag(effects,"hasMomentumAfter") || flag(effects,"quizMomentum"),flag(effects,"hasRecoveryAfter") || flag(effects,"quizRecovery"),
                flag(effects,"momentumConsumed"),flag(effects,"recoveryConsumed"),flag(effects,"momentumGranted"),flag(effects,"recoveryGranted"),
                flag(effects,"eliminatedNow"),effects.containsKey("playerState")?PlayerState.valueOf((String)effects.get("playerState")):p.getPlayerState(),
                effects.get("eliminatedAtMs") instanceof Number n?n.longValue():null,effects.get("eliminatedQuestionIndex") instanceof Number n?n.intValue():null,
                a.getSchemaVersion()==2?number(effects,"totalCorrectAnswerTimeMs",p.getTotalCorrectAnswerTimeMs()).longValue():null,
                a.getSchemaVersion()==2?number(effects,"ruleDelta",a.getScoreDelta()).intValue():null);
    }
    static String imageRef(GameQuestion q) {
        return q.getMode()==vn.edu.multigame.game.enums.GameMode.IMAGE_WORD?(String)q.getPayload().get("mediaRef"):q.getImageRef();
    }
    static Map<Option,String> options(GameQuestion q) {
        return q.getMode()==vn.edu.multigame.game.enums.GameMode.QUIZ?Map.of(Option.A,q.getOptionA(),Option.B,q.getOptionB(),Option.C,q.getOptionC(),Option.D,q.getOptionD()):Map.of();
    }
    /** Never expose secret-bearing payload before scoring. */
    static Map<String,Object> publicPayload(GameQuestion q,boolean scored) {
        if(q.getPayload()==null) return null;
        if(q.getMode()==vn.edu.multigame.game.enums.GameMode.SONG) {
            var payload=new LinkedHashMap<String,Object>();
            if(scored)payload.putAll(q.getPayload());else payload.put("mediaRef",q.getPayload().get("mediaRef"));
            payload.put("openedAtMs",q.getOpenedAtMs());return Map.copyOf(payload);
        }
        if(q.getMode()==vn.edu.multigame.game.enums.GameMode.CLUES) {
            var payload=new LinkedHashMap<String,Object>();
            var hints=(List<?>)q.getPayload().get("hints");
            payload.put("hints",List.copyOf(hints.subList(0,q.getReleasedHintCount())));
            if(scored) {payload.put("acceptedAnswers",q.getPayload().get("acceptedAnswers"));payload.put("matchingPolicy",q.getPayload().get("matchingPolicy"));}
            return Map.copyOf(payload);
        }
        if(scored) return Map.copyOf(q.getPayload());
        if(q.getMode()==vn.edu.multigame.game.enums.GameMode.IMAGE_WORD) return Map.of("mediaRef",q.getPayload().get("mediaRef"));
        if(q.getMode()==vn.edu.multigame.game.enums.GameMode.VIETNAMESE_PUZZLE) return Map.of("pieces",q.getPayload().get("pieces"));
        if(q.getMode()==vn.edu.multigame.game.enums.GameMode.ORDERING) return Map.of("items",q.getPayload().get("items"));
        return Map.of();
    }
    private static boolean flag(Map<String,Object> data,String key) { return Boolean.TRUE.equals(data.get(key)); }
    private static Number number(Map<String,Object> data,String key,Number fallback) { return data.get(key) instanceof Number n?n:fallback; }
}
