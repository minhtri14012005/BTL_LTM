package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import vn.edu.multigame.achievements.model.*;
import vn.edu.multigame.achievements.repository.AwardRepository;
import vn.edu.multigame.achievements.service.*;
import vn.edu.multigame.admin.dto.SanctionRequest;
import vn.edu.multigame.admin.service.AdminSanctionTransactions;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
import vn.edu.multigame.game.dto.TypedAnswer;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.realtime.session.RoomBoundary;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.user.enums.UserRole;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL transaction fixtures through GameTransactions proxies, no active Game actor; browser covers queued gameplay. */
class AwardDecisionIT extends GameLifecycleFixture {
    @Autowired AwardTransactions awardWrites;
    @Autowired AwardReadService awardReads;
    @Autowired AwardRepository awardRows;
    @Autowired AdminSanctionTransactions sanctions;
    @Autowired RoomBoundary boundary;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean AccountAccessPolicy accountPolicy;
    @MockitoSpyBean AwardClock awardClock;
    @AfterEach void resetAwards(){reset(accountPolicy,awardClock);}
    record Match(Fixture fixture,long game,int count) {}
    Match match(int count){
        var host=account();var roster=List.of(account(),account(),account());
        long quiz=quizService.create(host.auth(),new CreateQuestionBankRequest("Award fixture",Visibility.PRIVATE,Collections.nCopies(count,new QuestionRequest("Question",null,null,null,List.of("yes"))),GameMode.RIDDLE)).id();quizIds.add(quiz);
        var config=new RoomConfigRequest(null,"Award room",8,null,Participation.SPECTATOR,List.of(new RoomStageRequest(GameMode.RIDDLE,quiz,count,1000L)));
        var room=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),config)).snapshot();roomIds.add(room.id());long revision=room.revision();
        roomOps.execute(host.auth(),room.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",revision),null,()->{},()->rooms.open(host.id(),room.id(),revision),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),room.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",room.roomCode(),"participation","PLAYER"),room.roomCode(),()->{},()->rooms.join(p.id(),room.id(),room.roomCode(),Participation.PLAYER),(a,b)->{});
        var ready=rooms.get(host.id(),room.id());long id=boundary.read(RoomBoundary.Scope.room(room.id()),()->transactions.startMultimodeSnapshot(host.id(),room.id(),ready.revision(),System.currentTimeMillis())).gameSessionId();gameIds.add(id);
        return new Match(new Fixture(host,roster,quiz,ready),id,count);
    }
    void question(Match m,int index,Set<Long> correct){
        long at=jdbc.queryForObject("select started_at_ms from game_session where id=?",Long.class,m.game())+index*2000L;
        if(index==1)transactions.open(m.game(),index,Phase.INTRO,d->PhaseWindow.open(index,Phase.INTRO,index*3L,0,at,d));
        transactions.open(m.game(),index,Phase.QUESTION_OPEN,d->PhaseWindow.open(index,Phase.QUESTION_OPEN,index*3L+1,0,at,d));
        var allowed=jdbc.queryForList("select p.user_id from game_question_participation qp join player_session p on p.id=qp.player_session_id join game_question q on q.id=qp.game_question_id where qp.game_session_id=? and q.order_index=?",Long.class,m.game(),index);
        for(var p:m.fixture().roster())if(allowed.contains(p.id()))transactions.acceptTyped(m.game(),p.id(),index,null,new TypedAnswer(correct.contains(p.id())?"yes":"wrong",null),at+100,100);
        transactions.transition(m.game(),index,Phase.QUESTION_OPEN,Phase.QUESTION_CLOSED);transactions.transition(m.game(),index,Phase.QUESTION_CLOSED,Phase.SCORING);transactions.score(m.game(),index,at+1000);
    }
    Match completed(){var m=match(10);for(int i=1;i<=10;i++)question(m,i,new HashSet<>(m.fixture().roster().stream().map(Account::id).toList()));return m;}
    String state(long game){return jdbc.queryForObject("select state from game_award_decision where game_session_id=?",String.class,game);}
    long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    void finalizeMatch(Match m){awardReconciler.dispatch(m.game());}
    Account admin(){var a=account();jdbc.update("update app_user set role='ADMIN' where id=?",a.id());var u=users.findById(a.id()).orElseThrow();var p=new AuthPrincipal(u);return new Account(a.id(),a.name(),org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    void sanction(Account admin,long user,String action,long version){var req=new SanctionRequest(UUID.randomUUID().toString(),version,null,"Award race fixture");sanctions.apply(admin.auth(),user,action,req,action+req.requestId());}
    TransactionTemplate tx(){var t=new TransactionTemplate(manager);t.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);t.setTimeout(8);return t;}
    void await(CountDownLatch l){try{assertThat(l.await(6,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new AssertionError(e);}}

    @Test void resultCommitLeavesPendingThenRetryCommitsLedgerOnceWithoutChangingHistory()throws Exception{
        var m=completed();assertThat(state(m.game())).isEqualTo("PENDING");assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isZero();
        var before=jdbc.queryForList("select score,final_rank,total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",m.game());
        var client=new Client(m.fixture().host());assertThat(client.call("GET","/api/games/"+m.game()+"/awards",null).body()).contains("AWARDS_PENDING");
        finalizeMatch(m);finalizeMatch(m);assertThat(state(m.game())).isEqualTo("DECIDED");
        assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isEqualTo(9);
        assertThat(count("select sum(official_game_delta) from game_award_participant where game_session_id=?",m.game())).isEqualTo(3);
        assertThat(jdbc.queryForList("select score,final_rank,total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",m.game())).isEqualTo(before);
        var body=client.call("GET","/api/games/"+m.game()+"/awards",null).body();assertThat(body).contains("AWARDS_DECIDED","PERFECT","FAST","CUP").doesNotContain("banVersion","private_evidence","password","gameIntents");
        assertThat(new Client(account()).call("GET","/api/games/"+m.game()+"/awards",null).statusCode()).isEqualTo(403);
        assertThat(client.call("POST","/api/games/"+m.game()+"/awards",Map.of("cupDelta",100)).statusCode()).isEqualTo(403);
    }
    @Test void rollbackLeavesNoPartialAwardAndRetryCanFinish(){var m=completed();assertThatThrownBy(()->tx().execute(s->{awardWrites.finalizeGame(m.game());throw new IllegalStateException("Crash before commit");})).hasMessage("Crash before commit");assertThat(state(m.game())).isEqualTo("PENDING");assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isZero();assertThat(count("select count(*) from game_award_participant where game_session_id=?",m.game())).isZero();finalizeMatch(m);assertThat(state(m.game())).isEqualTo("DECIDED");}
    @Test void mismatchedPendingReceiptCannotGrantAwardsUnderAnotherPolicy(){var m=completed();jdbc.update("delete from game_award_decision where game_session_id=?",m.game());jdbc.update("insert into game_award_decision(game_session_id,policy_version,policy_hash,result_revision,created_at_ms) select id,'UNKNOWN',repeat('0',64),revision,finished_at_ms from game_session where id=?",m.game());assertThatThrownBy(()->finalizeMatch(m)).hasMessage("AWARD_EVIDENCE_MISMATCH");assertThat(state(m.game())).isEqualTo("PENDING");assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isZero();}
    @Test void concurrentFinalizersAndLostDeliveryCannotDuplicate(){var m=completed();try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->finalizeMatch(m));var b=pool.submit(()->finalizeMatch(m));assertThatCode(()->{a.get(8,TimeUnit.SECONDS);b.get(8,TimeUnit.SECONDS);}).doesNotThrowAnyException();}assertThat(count("select count(*) from achievement_award where game_session_id=? and award_type='CUP'",m.game())).isEqualTo(3);finalizeMatch(m);assertThat(state(m.game())).isEqualTo("DECIDED");}
    @Test void banAfterFinishedBeforeAwardExcludesUserWithoutGameIntentAndAfterAwardDoesNotRevoke(){var m=completed();var a=admin();long u=m.fixture().roster().getFirst().id();sanction(a,u,"INDEFINITE",0);assertThat(count("select count(*) from account_effect_intent where game_session_id=?",m.game())).isZero();finalizeMatch(m);assertThat(count("select count(*) from achievement_award where game_session_id=? and user_id=?",m.game(),u)).isZero();long peer=m.fixture().roster().get(1).id();sanction(a,peer,"INDEFINITE",0);finalizeMatch(m);assertThat(count("select count(*) from achievement_award where game_session_id=? and user_id=? and award_type='CUP'",m.game(),peer)).isEqualTo(1);}
    @Test void activeBanIntentSurvivesUnbanAndExcludedOnlyCorrectCannotQualify(){var m=match(10);var a=admin();long u=m.fixture().roster().getFirst().id();question(m,1,Set.of(u));sanction(a,u,"INDEFINITE",0);sanction(a,u,"UNBAN",1);for(int i=2;i<=10;i++)question(m,i,Set.of());assertThat(count("select count(*) from account_effect_intent where game_session_id=? and effect_type='GAME'",m.game())).isEqualTo(1);finalizeMatch(m);assertThat(state(m.game())).isEqualTo("NOT_QUALIFIED");assertThat(awardReads.read(m.fixture().host().id(),m.game()).qualification().reasonCodes()).contains("NO_ELIGIBLE_CORRECT");finalizeMatch(m);assertThat(count("select sum(official_game_delta) from game_award_participant where game_session_id=?",m.game())).isZero();}
    @Test void unbanBeforeAwardWithoutGameTargetPermitsCurrentEligibleState(){var m=completed();var a=admin();long u=m.fixture().roster().getFirst().id();sanction(a,u,"INDEFINITE",0);sanction(a,u,"UNBAN",1);finalizeMatch(m);assertThat(count("select count(*) from achievement_award where game_session_id=? and user_id=? and award_type='CUP'",m.game(),u)).isEqualTo(1);}
    @Test void cancelledAndInterruptedNeverScorePendingAnswerToMakeAward(){for(var reason:List.of(EndReason.CANCELLED,EndReason.SERVER_INTERRUPTED)){var m=match(10);long at=System.currentTimeMillis()+100;transactions.open(m.game(),1,Phase.INTRO,d->PhaseWindow.open(1,Phase.INTRO,1,0,at,d));transactions.open(m.game(),1,Phase.QUESTION_OPEN,d->PhaseWindow.open(1,Phase.QUESTION_OPEN,2,0,at,d));transactions.acceptTyped(m.game(),m.fixture().roster().getFirst().id(),1,null,new TypedAnswer("yes",null),at+10,10);if(reason==EndReason.CANCELLED)transactions.cancel(m.game(),m.fixture().host().id(),at+100);else transactions.interrupt(m.game(),at+100);finalizeMatch(m);assertThat(state(m.game())).isEqualTo("NOT_QUALIFIED");assertThat(count("select count(*) from answer where game_session_id=? and answer_status='ACCEPTED_UNSCORED'",m.game())).isEqualTo(1);assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isZero();}}
    @Test void shortCompletedIsTerminalNotQualifiedAndImmutable(){var m=match(1);question(m,1,Set.of(m.fixture().roster().getFirst().id()));finalizeMatch(m);assertThat(state(m.game())).isEqualTo("NOT_QUALIFIED");assertThatThrownBy(()->jdbc.update("update game_award_decision set state='PENDING',revision=0,decided_at_ms=null,eligibility_evaluated_at_ms=null where game_session_id=?",m.game())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(count("select sum(official_game_delta) from game_award_participant where game_session_id=?",m.game())).isZero();}
    @Test void missingSeedReconcilesAndV1NeverUsesCurrentDefaults()throws Exception{
        var m=completed();jdbc.update("delete from game_award_decision where game_session_id=?",m.game());assertThat(awardReads.read(m.fixture().host().id(),m.game()).awardState()).isEqualTo("AWARDS_PENDING");assertThat(count("select count(*) from game_award_decision where game_session_id=?",m.game())).isZero();finalizeMatch(m);assertThat(state(m.game())).isEqualTo("DECIDED");
        var legacy=fixture();long g=start(legacy);runtime.interrupt(g).get(5,TimeUnit.SECONDS);assertThat(awardReads.read(legacy.host().id(),g).availability()).isEqualTo("UNSUPPORTED_SCHEMA");assertThat(count("select count(*) from game_award_decision where game_session_id=?",g)).isZero();
    }
    @Test void friendlyMetadataAndPreActivationV2CannotBeRetroactivelyAwarded(){
        var m=completed();
        // Exclusion-only metadata fixtures, not a playable Friendly system or reservation implementation.
        for(String type:List.of("NORMAL","FRIENDLY_1V1")){
            var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(c->{var p=c.prepareStatement("insert into game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,current_question_index,config_snapshot,started_at_ms,finished_at_ms,schema_version,rules_version,v2_config_snapshot,current_stage_index,game_type) select room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,current_question_index,config_snapshot,started_at_ms,finished_at_ms,schema_version,rules_version,v2_config_snapshot,current_stage_index,? from game_session where id=?",java.sql.Statement.RETURN_GENERATED_KEYS);p.setString(1,type);p.setLong(2,m.game());return p;},key);
            long id=key.getKey().longValue();gameIds.add(id);
            jdbc.update("insert into game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot,membership_generation) select ?,room_id,user_id,role,participation,display_name_snapshot,membership_generation from game_member where game_session_id=? and user_id=?",id,m.game(),m.fixture().host().id());
            awardReconciler.dispatch(id);var view=awardReads.read(m.fixture().host().id(),id);
            assertThat(view.availability()).isEqualTo(type.equals("NORMAL")?"NO_POLICY_SNAPSHOT":"EXCLUDED_GAME_TYPE");assertThat(view.awardState()).isNull();
            assertThat(count("select count(*) from game_award_decision where game_session_id=?",id)).isZero();assertThat(count("select count(*) from achievement_award where game_session_id=?",id)).isZero();
        }
    }
    @Test void banHoldingUserCommitsWhileAwardOwnsRoomAndWaitsForUser()throws Exception{
        var m=completed();var a=admin();long u=m.fixture().roster().getFirst().id();var locked=new CountDownLatch(1);var go=new CountDownLatch(1);var awardWaiting=new CountDownLatch(1);
        doAnswer(c->{if(Thread.currentThread().getName().equals("award-race") && (long)c.getArgument(0)==u)awardWaiting.countDown();return c.callRealMethod();}).when(accountPolicy).lock(anyLong());
        try(var pool=Executors.newFixedThreadPool(2)){
            var ban=pool.submit(()->tx().execute(s->{accountPolicy.lock(u);locked.countDown();await(go);sanction(a,u,"INDEFINITE",0);return true;}));await(locked);
            var award=pool.submit(()->{Thread.currentThread().setName("award-race");finalizeMatch(m);});await(awardWaiting);go.countDown();ban.get(8,TimeUnit.SECONDS);award.get(8,TimeUnit.SECONDS);
        }finally{go.countDown();}
        assertThat(count("select count(*) from achievement_award where game_session_id=? and user_id=?",m.game(),u)).isZero();assertThat(state(m.game())).isEqualTo("DECIDED");
    }
    @Test void awardUserFenceCommitsFirstAndBanCannotRetroactivelyChangeDecision()throws Exception{
        var m=completed();var a=admin();long u=m.fixture().roster().getFirst().id();var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(c->{var result=c.callRealMethod();if(Thread.currentThread().getName().equals("award-first") && (long)c.getArgument(0)==u){held.countDown();await(release);}return result;}).when(accountPolicy).lock(anyLong());
        try(var pool=Executors.newFixedThreadPool(2)){
            var award=pool.submit(()->{Thread.currentThread().setName("award-first");finalizeMatch(m);});await(held);var ban=pool.submit(()->sanction(a,u,"INDEFINITE",0));release.countDown();award.get(8,TimeUnit.SECONDS);ban.get(8,TimeUnit.SECONDS);
        }finally{release.countDown();}
        finalizeMatch(m);assertThat(count("select count(*) from achievement_award where game_session_id=? and user_id=? and award_type='CUP'",m.game(),u)).isEqualTo(1);
    }
    @Test void expiryCrossedBeforeCommitRollsBackAndFreshRetryUsesNewEvaluation(){var m=completed();var a=admin();long u=m.fixture().roster().getFirst().id();var request=new SanctionRequest(UUID.randomUUID().toString(),0L,1,"TEMP fixture");var receipt=sanctions.apply(a.auth(),u,"TEMP",request,request.requestId());long expiry=receipt.committedUntilMs();doReturn(expiry-1,expiry-1,expiry+1).when(awardClock).now();assertThatThrownBy(()->finalizeMatch(m)).isInstanceOf(org.springframework.dao.TransientDataAccessException.class);assertThat(state(m.game())).isEqualTo("PENDING");assertThat(count("select count(*) from achievement_award where game_session_id=?",m.game())).isZero();doReturn(expiry+1).when(awardClock).now();finalizeMatch(m);assertThat(count("select count(*) from achievement_award where game_session_id=? and user_id=? and award_type='CUP'",m.game(),u)).isEqualTo(1);}
}
