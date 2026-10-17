package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.*;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edu.multigame.admin.dto.KickRequest;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.enums.UserRole;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** T08 real MySQL/HTTP/raw WS, controlled clock and retry barriers, no gameplay mocks. */
class GameKickIT extends GameNetworkFixture {
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean GameAccessPolicy access;
    @MockitoSpyBean GameProjection projection;
    @MockitoSpyBean vn.edu.multigame.user.repository.UserRepository userLocks;
    @Autowired vn.edu.multigame.room.repository.RoomMemberRepository memberships;
    @Autowired PlatformTransactionManager tx;
    @Autowired vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry sockets;
    @Autowired org.springframework.context.event.ApplicationEventMulticaster eventBus;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entities;
    @AfterEach void resetKickSpies(){reset(access,projection,userLocks);}
    record Admin(Account account,Client client) {}
    Admin admin()throws Exception{
        var user=new UserAccount();String secret=UUID.randomUUID()+"!Aa7";user.setUsername("kick_"+UUID.randomUUID().toString().replace("-","").substring(0,15));user.setDisplayName("Admin fixture");user.setPasswordHash(passwords.encode(secret));user.setRole(UserRole.ADMIN);user.setCreatedAtMs(System.currentTimeMillis());users.saveAndFlush(user);userIds.add(user.getId());
        var principal=new AuthPrincipal(user);var account=new Account(user.getId(),user.getUsername(),UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));return new Admin(account,new Client(account,secret));
    }
    Fixture modern(boolean quiz,boolean secondStage){
        var host=account();var author=account();var roster=List.of(account(),account(),account());var plan=new ArrayList<RoomStageRequest>();long source;
        if(quiz){source=quiz(author,10);plan.add(new RoomStageRequest(GameMode.QUIZ,source,10,20000L));}
        else{var created=quizService.create(host.auth(),new CreateQuestionBankRequest("Kick fixture",Visibility.PRIVATE,Collections.nCopies(secondStage?1:2,new QuestionRequest("Secret",null,null,null,List.of("answer"))),GameMode.RIDDLE));source=created.id();quizIds.add(source);plan.add(new RoomStageRequest(GameMode.RIDDLE,source,secondStage?1:2,20000L));}
        if(secondStage){long q=quiz(author,10);plan.add(new RoomStageRequest(GameMode.QUIZ,q,10,20000L));}
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"Kick",10,null,Participation.SPECTATOR,plan))).snapshot();roomIds.add(r.id());long rev=r.revision();
        roomOps.execute(host.auth(),r.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",rev),null,()->{},()->rooms.open(host.id(),r.id(),rev),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),r.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),()->{},()->rooms.join(p.id(),r.id(),r.roomCode(),Participation.PLAYER),(a,b)->{});
        return new Fixture(host,roster,source,rooms.get(host.id(),r.id()));
    }
    List<Wire> online(Fixture f)throws Exception{var wires=new ArrayList<Wire>();for(var p:f.roster()){var wire=new Wire(p);wire.subscribe(f);wires.add(wire);}return wires;}
    long startModern(Fixture f){int count=f.room().stages().stream().mapToInt(s->s.questionCount()).sum();var s=operations.start(f.host().auth(),f.room().id(),new StartGameRequest(UUID.randomUUID().toString(),f.room().revision(),count),()->{});gameIds.add(s.gameSessionId());installed.add(s.gameSessionId());observer.next("INTRO_STARTED",s.gameSessionId(),1);return s.gameSessionId();}
    void ready(List<Wire> ws,long id,int index)throws Exception{for(var w:ws)accepted(w.response(command("CONTINUE",id,index,Map.of())));}
    void advance(GameSnapshot phase){long due=phase.deadlineEpochMs()-clock.epoch;waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.due()==due));scheduler.advance(Math.max(due,clock.mono.get()));}
    KickRequest request(long user){return new KickRequest(UUID.randomUUID().toString(),user,"Fixture moderation");}
    com.fasterxml.jackson.databind.JsonNode kick(Admin admin,long id,KickRequest request)throws Exception{var r=admin.client().call("POST","/api/admin/games/"+id+"/kick",request);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    void text(Wire w,long id,int index)throws Exception{accepted(w.response(command("ANSWER",id,index,Map.of("text","answer"))));}
    long outcome(long id,int index,long user){return jdbc.queryForObject("select count(*) from answer a join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id where a.game_session_id=? and q.order_index=? and p.user_id=?",Long.class,id,index,user);}
    long participation(long id,int index,long user){return jdbc.queryForObject("select count(*) from game_question_participation s join player_session p on p.id=s.player_session_id join game_question q on q.id=s.game_question_id where s.game_session_id=? and q.order_index=? and p.user_id=?",Long.class,id,index,user);}

    @Test void kickOpenKeepsFrozenWaitSetAndNoAnswerThenExcludesNextQuestionAndBlocksAllIngress()throws Exception{
        var f=modern(false,false);var ws=online(f);var admin=admin();long id=startModern(f);ready(ws,id,1);var q=observer.next("QUESTION_START",id,1);long target=f.roster().getFirst().id();var request=request(target);
        var applied=kick(admin,id,request);assertThat(applied.path("status").asText()).isEqualTo("KICK_APPLIED");assertThat(applied.path("occupancyReleased").asBoolean()).isTrue();assertThat(applied.path("membershipDetached").asBoolean()).isTrue();
        ws.getFirst().next(m->m.path("type").asText().equals("GAME_ACCESS_REVOKED"));
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where user_id=?",Long.class,target)).isZero();
        assertThat(jdbc.queryForObject("select status from room_member where room_id=? and user_id=?",String.class,f.room().id(),target)).isEqualTo("LEFT");
        assertThat(participation(id,1,target)).isEqualTo(1);assertThat(outcome(id,1,target)).isZero();
        assertThat(new Client(f.roster().getFirst()).call("GET","/api/games/"+id+"/snapshot",null).statusCode()).isEqualTo(403);
        for(String type:List.of("ANSWER","USE_SPIN","USE_STAR","CONTINUE"))rejected(ws.getFirst().response(command(type,id,1,type.equals("ANSWER")?Map.of("text","answer"):Map.of())),"GAME_KICKED");
        rejected(ws.getFirst().response(envelope("RECONNECT","GAME",id,null,Map.of())),"GAME_KICKED");
        text(ws.get(1),id,1);text(ws.get(2),id,1);assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        advance(q);var result=observer.next("QUESTION_RESULT",id,1);assertThat(result.results().stream().filter(r->r.userId()==target).findFirst().orElseThrow().outcome()).isEqualTo(AnswerStatus.NO_ANSWER);
        advance(result);observer.next("QUESTION_START",id,2);assertThat(participation(id,2,target)).isZero();text(ws.get(1),id,2);text(ws.get(2),id,2);observer.next("GAME_END",id,2);assertThat(outcome(id,2,target)).isZero();
        assertThat(jdbc.queryForObject("select player_state from player_session where game_session_id=? and user_id=?",String.class,id,target)).isEqualTo("PLAYING");
        var history=new Client(f.roster().getFirst()).call("GET","/api/games/history/"+id,null);assertThat(history.statusCode()).isEqualTo(200);assertThat(history.body()).contains("KICKED_BY_ADMIN","NO_ANSWER");
        assertThat(kick(admin,id,request).path("replayed").asBoolean()).isTrue();assertThat(jdbc.queryForObject("select count(*) from admin_kick_action where game_session_id=?",Long.class,id)).isEqualTo(1);
        assertThatThrownBy(()->transactions.read(id,target)).isInstanceOf(GameFailure.class).hasMessage("GAME_KICKED");
    }
    @Test void closeThenKickThenScoringPreservesAcceptedUnscoredAndScoresExactlyOnce()throws Exception{preservesAccepted(false);}
    @Test void scoringRetryThenKickPreservesAcceptedUnscoredAndScoresExactlyOnce()throws Exception{preservesAccepted(true);}
    void preservesAccepted(boolean duringScoring)throws Exception{
        var f=modern(false,false);var ws=online(f);var admin=admin();long id=startModern(f);ready(ws,id,1);observer.next("QUESTION_START",id,1);
        var committed=new CountDownLatch(1);var release=new CountDownLatch(1);var admitted=new CountDownLatch(1);
        String pause=duringScoring?"SCORING_STARTED":"QUESTION_CLOSED";
        org.springframework.context.ApplicationListener<org.springframework.context.ApplicationEvent> listener=event->{
            if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload && payload.getPayload() instanceof GameLifecycleEvent e && e.snapshot().gameSessionId()==id && e.type().equals(pause)){
                committed.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Committed phase barrier timeout");}catch(InterruptedException ex){throw new AssertionError(ex);}
            }
        };eventBus.addApplicationListener(listener);
        if(duringScoring)doThrow(new TransientDataAccessResourceException("Controlled scorer retry")).doCallRealMethod().when(transactions).score(eq(id),eq(1),anyLong());
        doAnswer(c->{var result=c.callRealMethod();admitted.countDown();return result;}).when(runtime).administrative(eq(id),any());
        try(var pool=Executors.newSingleThreadExecutor()){
            for(var w:ws)text(w,id,1);assertThat(committed.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and answer_status='ACCEPTED_UNSCORED'",Long.class,id)).isEqualTo(3);
            var kicked=pool.submit(()->kick(admin,id,request(f.roster().getFirst().id())));assertThat(admitted.await(5,TimeUnit.SECONDS)).isTrue();release.countDown();assertThat(kicked.get(5,TimeUnit.SECONDS).path("status").asText()).isEqualTo("KICK_APPLIED");
            if(duringScoring)scheduler.retry(100);observer.next("QUESTION_RESULT",id,1);
            assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and answer_status='CORRECT' and scored_at_ms is not null",Long.class,id)).isEqualTo(3);
            transactions.score(id,1,clock.sample().epochMs());assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(10);assertThat(answerCount(id)).isEqualTo(3);
        }finally{release.countDown();eventBus.removeApplicationListener(listener);}
    }
    @Test void introKickDoesNotAutoReadyAndNewStageNoLongerWaitsForTarget()throws Exception{
        var f=modern(false,true);var ws=online(f);var admin=admin();long id=startModern(f);var intro=runtime.snapshot(id,f.host().id());long target=f.roster().getFirst().id();kick(admin,id,request(target));
        ready(ws.subList(1,3),id,1);assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.INTRO);assertThat(runtime.snapshot(id,f.host().id()).deadlineEpochMs()).isEqualTo(intro.deadlineEpochMs());assertThat(outcome(id,1,target)).isZero();
        advance(intro);observer.next("QUESTION_START",id,1);assertThat(participation(id,1,target)).isZero();for(var w:ws.subList(1,3))text(w,id,1);
        advance(observer.next("QUESTION_RESULT",id,1));observer.next("INTRO_STARTED",id,2);ready(ws.subList(1,3),id,2);observer.next("DECISION_STARTED",id,2);assertThat(outcome(id,2,target)).isZero();
    }
    @Test void decisionKickKeepsConsumedSpinStarEvidenceWithoutUnopenedOutcomeOrRefund()throws Exception{
        var f=modern(true,false);var ws=online(f);var admin=admin();long id=startModern(f);ready(ws,id,1);var decision=observer.next("DECISION_STARTED",id,1);long target=f.roster().getFirst().id();
        doReturn(SpinEffect.BONUS).when(spins).select(any(),any());accepted(ws.getFirst().response(command("USE_SPIN",id,1,Map.of())));accepted(ws.getFirst().response(command("USE_STAR",id,1,Map.of())));kick(admin,id,request(target));
        advance(decision);var q=observer.next("QUESTION_START",id,1);assertThat(participation(id,1,target)).isZero();assertThat(outcome(id,1,target)).isZero();
        for(var w:ws.subList(1,3))accepted(w.response(command("ANSWER",id,1,Map.of("option","D"))));advance(observer.next("QUESTION_RESULT",id,1));observer.next("DECISION_STARTED",id,2);
        var row=jdbc.queryForMap("select remaining_spins,star_available,current_spin from player_session where game_session_id=? and user_id=?",id,target);assertThat(((Number)row.get("remaining_spins")).intValue()).isZero();assertThat(row.get("star_available")).isIn(false,0);assertThat(row.get("current_spin")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from game_decision_action d join player_session p on p.id=d.player_session_id where d.game_session_id=? and p.user_id=?",Long.class,id,target)).isEqualTo(2);
        var cancel=new Client(f.host()).call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString()));assertThat(cancel.statusCode()).isEqualTo(200);
        var history=json.readTree(new Client(f.roster().getFirst()).call("GET","/api/games/history/"+id,null).body());assertThat(history.path("decisions")).hasSize(2);assertThat(history.path("decisions").toString()).contains("BONUS","USE_STAR");assertThat(outcome(id,1,target)).isZero();
    }
    @Test void failedCommitRollsBackRestrictionAuditOccupancyDetachAndProvisionalFence()throws Exception{
        var f=modern(false,false);var ws=online(f);var admin=admin();long id=startModern(f);long target=f.roster().getFirst().id();var input=request(target);
        doThrow(new TransientDataAccessResourceException("Before commit rollback")).doCallRealMethod().when(projection).copy(argThat(g->g.getId()==id));
        var failure=admin.client().call("POST","/api/admin/games/"+id+"/kick",input);assertThat(failure.statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("select count(*) from game_participation_restriction where game_session_id=?",Long.class,id)).isZero();assertThat(jdbc.queryForObject("select count(*) from admin_kick_action where game_session_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select game_session_id from user_active_game where user_id=?",Long.class,target)).isEqualTo(id);assertThat(jdbc.queryForObject("select status from room_member where room_id=? and user_id=?",String.class,f.room().id(),target)).isEqualTo("JOINED");assertThat(access.beginSend(id,target)).isTrue();
        assertThat(kick(admin,id,input).path("status").asText()).isEqualTo("KICK_APPLIED");assertThat(jdbc.queryForObject("select count(*) from admin_kick_action where game_session_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void servletRoleCsrfTargetGuardsAndDurableRequestFingerprintCannotBeBypassed()throws Exception{
        var base=modern(true,false);var spectator=account();rooms.join(spectator.id(),base.room().id(),base.room().roomCode(),Participation.SPECTATOR);
        var current=rooms.get(base.host().id(),base.room().id());var config=new RoomConfigRequest(null,"Host Player",10,null,Participation.PLAYER,List.of(new RoomStageRequest(GameMode.QUIZ,base.quiz(),10,20000L)));
        var edited=rooms.edit(base.host().id(),current.id(),current.revision(),config).snapshot();var f=new Fixture(base.host(),base.roster(),base.quiz(),edited);var ws=online(f);var admin=admin();long id=startModern(f);long target=f.roster().getFirst().id();String path="/api/admin/games/"+id+"/kick";var input=request(target);
        var ordinary=new Client(f.roster().getFirst());assertThat(ordinary.call("POST",path,input).statusCode()).isEqualTo(403);assertThat(ordinary.call("GET","/api/admin/games/"+id+"/players",null).statusCode()).isEqualTo(403);
        String csrf=admin.client().csrf;admin.client().csrf=null;try{assertThat(admin.client().call("POST",path,input).statusCode()).isEqualTo(403);}finally{admin.client().csrf=csrf;}
        assertThat(admin.client().call("POST",path,new KickRequest("invalid",target,"Reason")).statusCode()).isEqualTo(400);
        for(var denied:Map.of(f.host().id(),"KICK_HOST_FORBIDDEN",spectator.id(),"KICK_PLAYER_REQUIRED",account().id(),"KICK_PLAYER_REQUIRED").entrySet()){
            var result=admin.client().call("POST",path,request(denied.getKey()));assertThat(result.statusCode()).as(result.body()).isEqualTo(409);assertThat(json.readTree(result.body()).path("code").asText()).isEqualTo(denied.getValue());
        }
        jdbc.update("update app_user set role='ADMIN' where id=?",target);var denied=admin.client().call("POST",path,input);assertThat(denied.statusCode()).isEqualTo(409);assertThat(json.readTree(denied.body()).path("code").asText()).isEqualTo("KICK_ADMIN_FORBIDDEN");jdbc.update("update app_user set role='USER' where id=?",target);
        assertThat(jdbc.queryForObject("select count(*) from admin_kick_action where game_session_id=?",Long.class,id)).isZero();
        kick(admin,id,input);var conflict=admin.client().call("POST",path,new KickRequest(input.requestId(),target,"Different reason"));assertThat(conflict.statusCode()).isEqualTo(409);assertThat(json.readTree(conflict.body()).path("code").asText()).isEqualTo("REQUEST_ID_REUSED");
        var noop=kick(admin,id,request(target));assertThat(noop.path("status").asText()).isEqualTo("ALREADY_APPLIED");assertThat(noop.path("occupancyReleased").asBoolean()).isFalse();assertThat(noop.path("membershipDetached").asBoolean()).isFalse();
        cancel(f,id);assertThat(admin.client().call("POST",path,request(f.roster().get(1).id())).statusCode()).isEqualTo(409);
    }
    Fixture otherRoom(Fixture x){var host=account();var roster=List.of(x.roster().getFirst(),account(),account());long source=quiz(host,10);return new Fixture(host,roster,source,room(host,source,roster));}
    long startOther(Fixture y){var request=new StartGameRequest(UUID.randomUUID().toString(),y.room().revision(),10);var started=operations.start(y.host().auth(),y.room().id(),request,()->{});gameIds.add(started.gameSessionId());installed.add(started.gameSessionId());return started.gameSessionId();}
    void cancel(Fixture f,long id)throws Exception{assertThat(new Client(f.host()).call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(200);}
    @Test void releaseLetsUserStartYAndLateFinishReplayOrOldGenerationDetachCannotClearNewState()throws Exception{
        var x=modern(false,false);var ws=online(x);var admin=admin();long gx=startModern(x);var y=otherRoom(x);long target=x.roster().getFirst().id();var input=request(target);
        assertThatThrownBy(()->startOther(y)).isInstanceOf(GameFailure.class).hasMessage("USER_ACTIVE_GAME");
        long row=jdbc.queryForObject("select id from room_member where room_id=? and user_id=?",Long.class,x.room().id(),target);long generation=jdbc.queryForObject("select membership_generation from room_member where id=?",Long.class,row);
        kick(admin,gx,input);long gy=startOther(y);observer.next("DECISION_STARTED",gy,1);cancel(x,gx);
        assertThat(jdbc.queryForObject("select game_session_id from user_active_game where user_id=?",Long.class,target)).isEqualTo(gy);
        assertThat(kick(admin,gx,input).path("replayed").asBoolean()).isTrue();assertThat(jdbc.queryForObject("select status from room_member where room_id=? and user_id=?",String.class,y.room().id(),target)).isEqualTo("JOINED");
        // Release Y before setting up rejoin g2; never manufacture a legal JOIN/START by keeping X occupancy.
        cancel(y,gy);rooms.join(target,x.room().id(),x.room().roomCode(),Participation.PLAYER);
        assertThat(jdbc.queryForObject("select membership_generation from room_member where id=?",Long.class,row)).isEqualTo(generation+1);
        int changed=new TransactionTemplate(tx).execute(s->memberships.detachGeneration(row,x.room().id(),target,generation,System.currentTimeMillis()));assertThat(changed).isZero();
        assertThat(jdbc.queryForObject("select status from room_member where id=?",String.class,row)).isEqualTo("JOINED");assertThat(kick(admin,gx,input).path("replayed").asBoolean()).isTrue();
        assertThatThrownBy(()->transactions.read(gx,target)).hasMessage("GAME_KICKED");
    }
    @Test void kickBeforeStartYCommitUsesUserFenceAndNeverDeletesYClaim()throws Exception{
        var x=modern(false,false);var ws=online(x);var admin=admin();long gx=startModern(x);var y=otherRoom(x);long target=x.roster().getFirst().id();
        var beforeCommit=new CountDownLatch(1);var commitRelease=new CountDownLatch(1);var startAttempt=new CountDownLatch(1);
        doAnswer(c->{c.callRealMethod();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void beforeCommit(boolean readOnly){beforeCommit.countDown();try{if(!commitRelease.await(10,TimeUnit.SECONDS))throw new AssertionError("Kick commit barrier timeout");}catch(InterruptedException e){throw new AssertionError(e);}}});return null;}).when(access).fenceForCommit(gx,target);
        try(var pool=Executors.newFixedThreadPool(2)){
            var kick=pool.submit(()->kick(admin,gx,request(target)));assertThat(beforeCommit.await(5,TimeUnit.SECONDS)).isTrue();
            doAnswer(c->{startAttempt.countDown();return Optional.ofNullable(entities.find(UserAccount.class,target,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));}).when(userLocks).findLockedById(target);
            var start=pool.submit(()->startOther(y));assertThat(startAttempt.await(5,TimeUnit.SECONDS)).isTrue();assertThat(start.isDone()).isFalse();commitRelease.countDown();
            assertThat(kick.get(5,TimeUnit.SECONDS).path("status").asText()).isEqualTo("KICK_APPLIED");long gy=start.get(5,TimeUnit.SECONDS);cancel(x,gx);assertThat(jdbc.queryForObject("select game_session_id from user_active_game where user_id=?",Long.class,target)).isEqualTo(gy);
        }finally{commitRelease.countDown();}
    }
    @Test void startYBeforeKickCannotDoubleBookAndRetryAfterReleaseSucceeds()throws Exception{
        var x=modern(false,false);var ws=online(x);var admin=admin();long gx=startModern(x);var y=otherRoom(x);long target=x.roster().getFirst().id();
        var locked=new CountDownLatch(1);var releaseStart=new CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean();
        // Spring Data's interface method cannot callRealMethod. This equivalent JPA
        // read takes the actual MySQL row lock; only the latch is test instrumentation.
        doAnswer(c->{var result=Optional.ofNullable(entities.find(UserAccount.class,target,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));if(once.compareAndSet(false,true)){locked.countDown();if(!releaseStart.await(10,TimeUnit.SECONDS))throw new AssertionError("Start barrier timeout");}return result;}).when(userLocks).findLockedById(target);
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=pool.submit(()->startOther(y));assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();var kick=pool.submit(()->kick(admin,gx,request(target)));releaseStart.countDown();
            assertThatThrownBy(()->start.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(GameFailure.class).hasRootCauseMessage("USER_ACTIVE_GAME");assertThat(kick.get(5,TimeUnit.SECONDS).path("status").asText()).isEqualTo("KICK_APPLIED");
            long gy=startOther(y);assertThat(jdbc.queryForObject("select game_session_id from user_active_game where user_id=?",Long.class,target)).isEqualTo(gy);
        }finally{releaseStart.countDown();}
    }
    @Test void phaseClockStartsAfterWaitingForTheUserCommitFence()throws Exception{
        var f=modern(false,false);var ws=online(f);long id=startModern(f);long target=f.roster().getFirst().id();var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        // T09 fences CONTINUE commits too. Let this target READY before holding
        // its User row; the remaining players can READY and OPEN then waits here.
        accepted(ws.getFirst().response(command("CONTINUE",id,1,Map.of())));
        try(var pool=Executors.newSingleThreadExecutor()){
            var holder=pool.submit(()->new TransactionTemplate(tx).execute(s->{jdbc.queryForObject("select id from app_user where id=? for update",Long.class,target);held.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("User lock barrier timeout");}catch(InterruptedException e){throw new AssertionError(e);}return null;}));assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
            doAnswer(c->{attempted.countDown();return Optional.ofNullable(entities.find(UserAccount.class,target,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));}).when(userLocks).findLockedById(target);
            ready(ws.subList(1,ws.size()),id,1);assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();long fresh=clock.mono.addAndGet(5000);release.countDown();holder.get(5,TimeUnit.SECONDS);
            var opened=observer.next("QUESTION_START",id,1);assertThat(opened.deadlineEpochMs()).isEqualTo(clock.epoch+fresh+20000);assertThat(opened.remainingMs()).isEqualTo(20000);
            assertThat(jdbc.queryForObject("select opened_at_ms from game_question where game_session_id=? and order_index=1",Long.class,id)).isEqualTo(clock.epoch+fresh);
        }finally{release.countDown();}
    }
    @Test void blockedSocketCannotDelayKickAndQueuedXFramesDropWhileYFifoSurvives()throws Exception{
        var f=modern(false,false);var ws=online(f);var admin=admin();long id=startModern(f);long target=f.roster().getFirst().id();
        var y=otherRoom(f);
        var login=new Client(f.roster().getFirst());String session=login.cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        var socket=mock(org.springframework.web.socket.WebSocketSession.class);when(socket.getId()).thenReturn("kick-slow-"+id);when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());when(socket.getPrincipal()).thenReturn(f.roster().getFirst().auth());when(socket.isOpen()).thenReturn(true);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var sent=new CopyOnWriteArrayList<String>();
        doAnswer(c->{String value=((org.springframework.web.socket.TextMessage)c.getArgument(0)).getPayload();sent.add(value);if(value.contains("HOLD_BEFORE_FENCE")){entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw new AssertionError("Slow writer latch timeout");}return null;}).when(socket).sendMessage(any());
        sockets.add(session,socket,target);sockets.connected(socket);sockets.subscribe(socket,f.room().id());
        try{
            sockets.send(socket,Map.of("control","HOLD_BEFORE_FENCE"));assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            var snapshot=runtime.snapshot(id,f.roster().getFirst().id());var frame=new vn.edu.multigame.realtime.message.common.ReconnectAck(1,"ACK","pending-x","RECONNECT",vn.edu.multigame.realtime.message.common.GameTarget.of(id),1,"ACCEPTED",snapshot.revision(),snapshot.serverTimeMs(),1,snapshot);
            sockets.send(socket,frame);sockets.send(socket,frame);
            try(var pool=Executors.newSingleThreadExecutor()){var result=pool.submit(()->kick(admin,id,request(target)));assertThat(result.get(2,TimeUnit.SECONDS).path("status").asText()).isEqualTo("KICK_APPLIED");assertThat(release.getCount()).isEqualTo(1);}
            long gy=startOther(y);var next=runtime.snapshot(gy,target);sockets.subscribe(socket,y.room().id());
            for(String requestId:List.of("Y-1","Y-2"))sockets.send(socket,new vn.edu.multigame.realtime.message.common.ReconnectAck(1,"ACK",requestId,"RECONNECT",vn.edu.multigame.realtime.message.common.GameTarget.of(gy),1,"ACCEPTED",next.revision(),next.serverTimeMs(),1,next));
            assertThat(jdbc.queryForObject("select game_session_id from user_active_game where user_id=?",Long.class,target)).isEqualTo(gy);
            release.countDown();verify(socket,timeout(3000)).sendMessage(argThat(m->m.getPayload().toString().contains("Y-2")));
            assertThat(String.join("\n",sent)).doesNotContain("pending-x","Secret");
            var ids=sent.stream().map(value->{try{return json.readTree(value).path("requestId").asText();}catch(Exception e){throw new AssertionError(e);}}).filter(v->v.startsWith("Y-")).toList();assertThat(ids).containsExactly("Y-1","Y-2");
        }finally{release.countDown();sockets.remove(session,socket);}
    }
}
