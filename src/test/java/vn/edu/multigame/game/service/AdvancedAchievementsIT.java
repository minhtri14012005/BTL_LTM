package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.multigame.achievements.model.ApprovedAwardPolicy;
import vn.edu.multigame.achievements.service.*;
import vn.edu.multigame.admin.dto.*;
import vn.edu.multigame.admin.service.*;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.realtime.session.RoomBoundary;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancedAchievementsIT extends AdvancedAwardFixture {
    @Autowired AwardTransactions awards;
    @Autowired AwardReadService reads;
    @Autowired ProfileReadService profiles;
    @Autowired PlatformTransactionManager manager;
    @Autowired AdminSanctionTransactions sanctions;
    @Autowired AdminKickTransactions kicks;
    Match fresh(){return match(List.of(GameMode.RIDDLE,GameMode.QUIZ,GameMode.CLUES),List.of(4,4,4));}
    void play(Match m,int from,int through){
        var ids=m.ids();for(int i=from;i<=through;i++){
            int local=(i-1)%4+1;var correct=new HashSet<Long>();var afk=new HashSet<Long>();
            if(i<=4){if(local>=3)correct.add(ids.get(0));else afk.add(ids.get(0));correct.add(ids.get(1));correct.add(ids.get(2));}
            else {correct.add(ids.get(0));if(local<=2)correct.add(ids.get(1));else afk.add(ids.get(1));if(local==1)correct.add(ids.get(2));}
            question(m,i,correct,afk);
        }
    }
    Match completed(){var m=fresh();play(m,1,12);return m;}
    long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    String state(Match m){return jdbc.queryForObject("select state from game_award_decision where game_session_id=?",String.class,m.game());}
    Account admin(){var a=account();jdbc.update("update app_user set role='ADMIN' where id=?",a.id());var p=new AuthPrincipal(users.findById(a.id()).orElseThrow());return new Account(a.id(),a.name(),org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    @Test void twoMilestonesAndComebackCommitOnceWithoutChangingHistoryAndSurviveProfileReads()throws Exception{
        var m=completed();var id=m.ids();var before=jdbc.queryForList("select score,final_rank,total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",m.game());
        assertThat(reads.read(id.get(0),m.game()).awards()).isEmpty();awardReconciler.dispatch(m.game());
        var view=reads.read(id.get(0),m.game());assertThat(view.policyVersion()).isEqualTo("AWARD_V2");assertThat(view.awards().stream().filter(a->a.type().equals("MULTITALENT")).map(a->a.userId())).containsExactlyInAnyOrder(id.get(0),id.get(1));
        var comeback=view.awards().stream().filter(a->a.type().equals("COMEBACK")).findFirst().orElseThrow();assertThat(comeback.userId()).isEqualTo(id.get(0));assertThat(comeback.evidence()).containsEntry("stageOneRank",3).containsEntry("finalEligibleRank",1);
        assertThat(jdbc.queryForList("select score,final_rank,total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",m.game())).isEqualTo(before);
        assertThat(profiles.own(id.get(0)).stats().correct()).isEqualTo(10);assertThat(profiles.own(id.get(0)).stats().participated()).isEqualTo(12);
        assertThat(profiles.own(id.get(1)).stats().correct()).isEqualTo(8);assertThat(profiles.own(id.get(1)).stats().participated()).isEqualTo(12);
        assertThat(count("select count(*) from answer where game_session_id=? and answer_status='NO_ANSWER'",m.game())).isEqualTo(6);
        assertThat(count("select sum(official_game_delta) from game_award_participant where game_session_id=?",m.game())).isEqualTo(3);assertThat(count("select sum(official_win_delta) from game_award_participant where game_session_id=?",m.game())).isEqualTo(1);
        for(int i=0;i<3;i++)awardReconciler.dispatch(m.game());assertThat(reads.read(id.get(0),m.game()).awards()).isEqualTo(view.awards());
        var c=new Client(m.fixture().roster().getFirst());assertThat(c.call("GET","/api/profiles/me",null).body()).contains("MULTITALENT","COMEBACK");
        var waiting=rooms.get(m.fixture().host().id(),m.fixture().room().id());rooms.close(m.fixture().host().id(),waiting.id(),waiting.revision());assertThat(profiles.own(id.get(0)).badges()).extracting(b->b.type()).contains("MULTITALENT","COMEBACK");
    }
    @Test void missingOrMismatchedStageTimeKeepsPendingWithNoPartialLedgerThenRepairRetries(){
        var m=completed();long user=m.ids().getFirst();String sql="select a.result_snapshot from answer a join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id where q.game_session_id=? and q.order_index=4 and p.user_id=?";
        var saved=jdbc.queryForObject(sql,String.class,m.game(),user);
        for(String expression:List.of("JSON_REMOVE(a.result_snapshot,'$.totalCorrectAnswerTimeMs')","JSON_SET(a.result_snapshot,'$.totalCorrectAnswerTimeMs',999999)")){
            jdbc.update("update answer a join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id set a.result_snapshot="+expression+" where q.game_session_id=? and q.order_index=4 and p.user_id=?",m.game(),user);
            assertThatThrownBy(()->awardReconciler.dispatch(m.game())).hasMessage("AWARD_STAGE_DATA_INCOMPLETE");assertThat(state(m)).isEqualTo("PENDING");assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isZero();assertThat(count("select count(*) from game_award_participant where game_session_id=?",m.game())).isZero();
        }
        jdbc.update("update answer a join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id set a.result_snapshot=? where q.game_session_id=? and q.order_index=4 and p.user_id=?",saved,m.game(),user);awardReconciler.dispatch(m.game());assertThat(state(m)).isEqualTo("DECIDED");
    }
    @Test void rollbackAndConcurrentRetryNeverCreateHalfOrDuplicateAdvancedAwards()throws Exception{
        var m=completed();new TransactionTemplate(manager).executeWithoutResult(s->{awards.finalizeGame(m.game());assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isEqualTo(4);s.setRollbackOnly();});
        assertThat(state(m)).isEqualTo("PENDING");assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isZero();
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->awardReconciler.dispatch(m.game()));var b=pool.submit(()->awardReconciler.dispatch(m.game()));a.get(8,TimeUnit.SECONDS);b.get(8,TimeUnit.SECONDS);}
        assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isEqualTo(4);
    }
    @Test void banBeforeDecisionExcludesCandidateFromBothStageAndFinalEligibleRanks(){
        var m=completed();var admin=admin();var request=new SanctionRequest(UUID.randomUUID().toString(),0L,null,"Advanced eligibility");sanctions.apply(admin.auth(),m.ids().getFirst(),"INDEFINITE",request,request.requestId());awardReconciler.dispatch(m.game());
        var view=reads.read(m.fixture().host().id(),m.game());assertThat(view.awards()).noneMatch(a->a.userId()==m.ids().getFirst());assertThat(view.awards()).noneMatch(a->a.type().equals("COMEBACK"));
        assertThat(view.awards().stream().filter(a->a.type().equals("MULTITALENT")).map(a->a.userId())).containsExactly(m.ids().get(1));assertThat(view.participants().stream().filter(p->p.userId()==m.ids().getFirst()).findFirst().orElseThrow().historyRank()).isEqualTo(1);
    }
    @Test void activeKickDoesNotInvalidateOtherEligiblePlayersStageProof(){
        var m=fresh();play(m,1,4);var admin=admin();var request=new KickRequest(UUID.randomUUID().toString(),m.ids().get(1),"Advanced Game restriction");boundary.read(RoomBoundary.Scope.room(m.fixture().room().id()),()->kicks.apply(admin.auth(),m.game(),request,request.requestId()));play(m,5,12);awardReconciler.dispatch(m.game());
        var view=reads.read(m.fixture().host().id(),m.game());assertThat(view.awards()).noneMatch(a->a.userId()==m.ids().get(1));assertThat(view.awards().stream().filter(a->a.type().equals("COMEBACK")).map(a->a.userId())).containsExactly(m.ids().get(0));
    }
    @Test void v1ActiveBeforeActivationFinishesUnderV1AndTerminalCannotGainNewBadges(){
        Match old;
        try(var mocked=mockStatic(ApprovedAwardPolicy.class,CALLS_REAL_METHODS)){var legacy=ApprovedAwardPolicy.approved();mocked.when(ApprovedAwardPolicy::active).thenReturn(legacy);old=fresh();}
        String before=jdbc.queryForObject("select award_policy_snapshot from game_session where id=?",String.class,old.game());play(old,1,12);awardReconciler.dispatch(old.game());
        var view=reads.read(old.fixture().host().id(),old.game());assertThat(view.policyVersion()).isEqualTo("AWARD_V1");assertThat(view.awards()).allSatisfy(a->assertThat(a.type()).isEqualTo("CUP"));
        awardReconciler.dispatch(old.game());assertThat(reads.read(old.fixture().host().id(),old.game()).awards()).isEqualTo(view.awards());assertThat(jdbc.queryForObject("select award_policy_snapshot from game_session where id=?",String.class,old.game())).isEqualTo(before);
        var modern=fresh();assertThat(jdbc.queryForObject("select JSON_UNQUOTE(JSON_EXTRACT(award_policy_snapshot,'$.policyId')) from game_session where id=?",String.class,modern.game())).isEqualTo("AWARD_V2");
    }
    @Test void shortOrCancelledNeverReceivesAdvancedDespiteModeMetrics(){
        var shortGame=match(List.of(GameMode.RIDDLE,GameMode.QUIZ,GameMode.CLUES),List.of(3,3,3));for(int i=1;i<=9;i++)question(shortGame,i,new HashSet<>(shortGame.ids()),Set.of());awardReconciler.dispatch(shortGame.game());assertThat(state(shortGame)).isEqualTo("NOT_QUALIFIED");assertThat(count("select count(*) from achievement_award where game_session_id=?",shortGame.game())).isZero();
        var cancelled=fresh();open(cancelled,1);transactions.cancel(cancelled.game(),cancelled.fixture().host().id(),System.currentTimeMillis());awardReconciler.dispatch(cancelled.game());assertThat(state(cancelled)).isEqualTo("NOT_QUALIFIED");
    }
}
