package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import vn.edu.multigame.admin.dto.*;
import vn.edu.multigame.admin.repository.BanEffectRepository;
import vn.edu.multigame.admin.service.*;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.game.dto.*;
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

/** Real MySQL, HTTP/raw WS and explicit transaction barriers. Deferred effects stay paused until requested. */
class AccountBanIT extends GameNetworkFixture {
    @Autowired PasswordEncoder passwords;
    @Autowired AccountAccessPolicy accountAccess;
    @Autowired AdminSanctionTransactions sanctions;
    @Autowired BanEffectRepository intents;
    @MockitoSpyBean BanEffectTransactions effects;
    @MockitoSpyBean BanEffectReconciler reconciler;
    @MockitoSpyBean vn.edu.multigame.game.repository.QuestionParticipationStore participationStore;
    @MockitoSpyBean vn.edu.multigame.user.repository.UserRepository userLocks;
    @Autowired vn.edu.multigame.realtime.idempotency.CommandFingerprint fingerprints;
    @Autowired PlatformTransactionManager manager;
    @Autowired vn.edu.multigame.room.repository.RoomMemberRepository memberships;
    @Autowired vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry sockets;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entities;
    @BeforeEach void pauseEffects(){doNothing().when(reconciler).reconcile();}
    @AfterEach void resetParticipation(){reset(participationStore,userLocks,effects);}
    record Admin(Account account,Client client) {}
    Admin admin()throws Exception{
        var user=new UserAccount();String password=UUID.randomUUID()+"!Aa7";user.setUsername("ban_"+UUID.randomUUID().toString().replace("-","").substring(0,15));user.setDisplayName("Admin fixture");user.setPasswordHash(passwords.encode(password));user.setRole(UserRole.ADMIN);user.setCreatedAtMs(System.currentTimeMillis());users.saveAndFlush(user);userIds.add(user.getId());
        var p=new AuthPrincipal(user);var a=new Account(user.getId(),user.getUsername(),UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));return new Admin(a,new Client(a,password));
    }
    Account fresh(Account user){var entity=users.findById(user.id()).orElseThrow();var p=new AuthPrincipal(entity);return new Account(user.id(),user.name(),UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    SanctionRequest request(long version,Integer minutes){return new SanctionRequest(UUID.randomUUID().toString(),version,minutes,"Moderation fixture");}
    String path(long user,String action){return "/api/admin/users/"+user+"/"+(action.equals("TEMP")?"temporary-ban":action.equals("UNBAN")?"unban":"ban");}
    com.fasterxml.jackson.databind.JsonNode apply(Admin admin,long user,String action,SanctionRequest request)throws Exception{
        var r=admin.client().call("POST",path(user,action),request);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());
    }
    SanctionResponse direct(Admin admin,long user,String command,SanctionRequest request){return sanctions.apply(admin.account().auth(),user,command,request,fingerprints.of(command,"ACCOUNT",user,null,request));}
    List<Long> targets(long action,String type){return jdbc.queryForList("select id from account_effect_intent where action_id=? and effect_type=? order by id",Long.class,action,type);}
    void dispatch(long action,String type)throws Exception{for(long id:targets(action,type))reconciler.dispatch(id);}
    long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    long participation(long game,int index,long user){return count("select count(*) from game_question_participation s join player_session p on p.id=s.player_session_id join game_question q on q.id=s.game_question_id where s.game_session_id=? and q.order_index=? and p.user_id=?",game,index,user);}
    long outcomes(long game,int index,long user){return count("select count(*) from answer a join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id where a.game_session_id=? and q.order_index=? and p.user_id=?",game,index,user);}
    Fixture modern(boolean quiz){
        var host=account();var author=account();var roster=List.of(account(),account(),account());long source;
        if(quiz)source=quiz(author,10);else{source=quizService.create(host.auth(),new CreateQuestionBankRequest("Ban questions",Visibility.PRIVATE,Collections.nCopies(2,new QuestionRequest("Secret",null,null,null,List.of("answer"))),GameMode.RIDDLE)).id();quizIds.add(source);}
        var config=new RoomConfigRequest(null,"Ban fixture",10,null,Participation.SPECTATOR,List.of(new RoomStageRequest(quiz?GameMode.QUIZ:GameMode.RIDDLE,source,quiz?10:2,20000L)));
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),config)).snapshot();roomIds.add(r.id());long rev=r.revision();
        roomOps.execute(host.auth(),r.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",rev),null,()->{},()->rooms.open(host.id(),r.id(),rev),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),r.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),()->{},()->rooms.join(p.id(),r.id(),r.roomCode(),Participation.PLAYER),(a,b)->{});
        return new Fixture(host,roster,source,rooms.get(host.id(),r.id()));
    }
    List<Wire> online(Fixture f)throws Exception{var ws=new ArrayList<Wire>();for(var p:f.roster()){var w=new Wire(p);w.subscribe(f);ws.add(w);}return ws;}
    long startModern(Fixture f){var s=operations.start(f.host().auth(),f.room().id(),new StartGameRequest(UUID.randomUUID().toString(),f.room().revision(),f.room().stages().getFirst().questionCount()),()->{});gameIds.add(s.gameSessionId());installed.add(s.gameSessionId());observer.next("INTRO_STARTED",s.gameSessionId(),1);return s.gameSessionId();}
    void ready(List<Wire> ws,long game)throws Exception{for(var w:ws)accepted(w.response(command("CONTINUE",game,1,Map.of())));}
    void advance(GameSnapshot s){long due=s.deadlineEpochMs()-clock.epoch;waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.due()==due));scheduler.advance(Math.max(due,clock.mono.get()));}
    void text(Wire w,long game,int index)throws Exception{accepted(w.response(command("ANSWER",game,index,Map.of("text","answer"))));}
    TransactionTemplate tx(){var tx=new TransactionTemplate(manager);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(8);return tx;}
    void await(CountDownLatch latch){try{assertThat(latch.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}

    @Test void banBeforeOpenCommitSeesDurableIntentDespitePausedEjectionAndUnban()throws Exception{
        var f=modern(true);var ws=online(f);var admin=admin();long game=startModern(f);ready(ws,game);var decision=observer.next("DECISION_STARTED",game,1);var user=f.roster().getFirst();
        doReturn(SpinEffect.BONUS).when(spins).select(any(),any());
        accepted(ws.getFirst().response(command("USE_SPIN",game,1,Map.of())));accepted(ws.getFirst().response(command("USE_STAR",game,1,Map.of())));
        var banned=apply(admin,user.id(),"INDEFINITE",request(0,null));long action=banned.path("actionId").asLong();
        assertThat(targets(action,"GAME")).hasSize(1);assertThat(intents.get(targets(action,"GAME").getFirst()).state()).isEqualTo("PENDING");
        apply(admin,user.id(),"UNBAN",request(1,null));advance(decision);observer.next("QUESTION_START",game,1);
        assertThat(participation(game,1,user.id())).isZero();assertThat(outcomes(game,1,user.id())).isZero();
        assertThat(count("select count(*) from game_decision_action where game_session_id=? and player_session_id=(select id from player_session where game_session_id=? and user_id=?)",game,game,user.id())).isEqualTo(2);
        var newSession=new Client(fresh(user));assertThat(newSession.call("GET","/api/games/"+game+"/snapshot",null).statusCode()).isEqualTo(403);
        assertThatThrownBy(()->runtime.snapshot(game,user.id())).isInstanceOf(GameFailure.class).hasMessage("GAME_KICKED");
        for(var w:ws.subList(1,3))accepted(w.response(command("ANSWER",game,1,Map.of("option","D"))));observer.next("QUESTION_RESULT",game,1);
        assertThat(intents.get(targets(action,"GAME").getFirst()).state()).isEqualTo("PENDING");assertThat(outcomes(game,1,user.id())).isZero();
        dispatch(action,"GAME");assertThat(count("select count(*) from user_active_game where user_id=?",user.id())).isZero();
        assertThat(new Client(f.host()).call("POST","/api/games/"+game+"/cancel",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(200);
        var history=json.readTree(newSession.call("GET","/api/games/history/"+game,null).body());assertThat(history.path("decisions").size()).isEqualTo(2);
        assertThat(outcomes(game,1,user.id())).isZero();assertThat(history.path("restrictions").toString()).contains("BANNED_BY_ADMIN");
        assertThat(accountAccess.state(user.id()).kind()).isEqualTo("NONE");
    }

    @Test void banHoldsUserButNeverWaitsForRoomAndOpenWaitsUntilBanCommit()throws Exception{
        var f=modern(true);var ws=online(f);var admin=admin();long game=startModern(f);ready(ws,game);var decision=observer.next("DECISION_STARTED",game,1);long user=f.roster().getFirst().id();
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()){
            var ban=pool.submit(()->tx().execute(s->{var receipt=direct(admin,user,"INDEFINITE",request(0,null));held.countDown();await(release);return receipt;}));
            await(held);advance(decision);
            assertThat(count("select count(*) from game_question_participation where game_session_id=?",game)).isZero();
            release.countDown();assertThat(ban.get(5,TimeUnit.SECONDS).committedVersion()).isEqualTo(1);observer.next("QUESTION_START",game,1);
            assertThat(participation(game,1,user)).isZero();
        }finally{release.countDown();}
    }

    @Test void openUserFenceCommitsFirstThenBanKeepsOpenedObligationAndExcludesNext()throws Exception{
        var f=modern(false);var ws=online(f);var admin=admin();long game=startModern(f),user=f.roster().getFirst().id();
        var opening=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{var result=call.callRealMethod();opening.countDown();await(release);return result;}).when(participationStore).capture(eq(game),anyLong(),anyLong(),anyCollection());
        try(var pool=Executors.newFixedThreadPool(2)){
            var ready=pool.submit(()->{ready(ws,game);return true;});await(opening);
            var ban=pool.submit(()->apply(admin,user,"INDEFINITE",request(0,null)));assertThat(ban.isDone()).isFalse();release.countDown();ready.get(5,TimeUnit.SECONDS);var q=observer.next("QUESTION_START",game,1);var receipt=ban.get(5,TimeUnit.SECONDS);
            assertThat(participation(game,1,user)).isEqualTo(1);text(ws.get(1),game,1);text(ws.get(2),game,1);assertThat(runtime.snapshot(game,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
            advance(q);var result=observer.next("QUESTION_RESULT",game,1);assertThat(result.results().stream().filter(r->r.userId()==user).findFirst().orElseThrow().outcome()).isEqualTo(AnswerStatus.NO_ANSWER);
            reset(participationStore);advance(result);observer.next("QUESTION_START",game,2);assertThat(participation(game,2,user)).isZero();assertThat(outcomes(game,2,user)).isZero();
            assertThat(receipt.path("committedVersion").asLong()).isEqualTo(1);
        }finally{release.countDown();}
    }

    @Test void banIntentInsertNeverTakesRoomGameForeignKeyLockWhileOpenOwnsGameAndWaitsForUser()throws Exception{
        var f=modern(true);var ws=online(f);var admin=admin();long game=startModern(f);ready(ws,game);var decision=observer.next("DECISION_STARTED",game,1);long user=f.roster().getFirst().id();
        var userHeld=new CountDownLatch(1);var openAtUser=new CountDownLatch(1);
        doAnswer(c->{if(Thread.currentThread().getName().startsWith("game-worker-"))openAtUser.countDown();return Optional.ofNullable(entities.find(UserAccount.class,user,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));}).when(userLocks).findLockedById(user);
        try(var pool=Executors.newSingleThreadExecutor()){
            var ban=pool.submit(()->tx().execute(s->{accountAccess.lock(user);userHeld.countDown();await(openAtUser);return direct(admin,user,"INDEFINITE",request(0,null));}));
            await(userHeld);advance(decision);assertThat(ban.get(5,TimeUnit.SECONDS).result()).isEqualTo("APPLIED");observer.next("QUESTION_START",game,1);assertThat(participation(game,1,user)).isZero();
        }
    }

    @Test void answerCommitBeforeBanIsRetainedAndScoredExactlyOnce()throws Exception{
        var f=modern(false);var ws=online(f);var admin=admin();long game=startModern(f),user=f.roster().getFirst().id();ready(ws,game);var q=observer.next("QUESTION_START",game,1);
        var saved=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var answer=pool.submit(()->tx().execute(s->{var c=transactions.acceptTyped(game,user,1,null,new TypedAnswer("answer",null),q.deadlineEpochMs()-20000+1,1);saved.countDown();await(release);return c;}));await(saved);
            var ban=pool.submit(()->apply(admin,user,"INDEFINITE",request(0,null)));assertThat(ban.isDone()).isFalse();release.countDown();answer.get(5,TimeUnit.SECONDS);ban.get(5,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select answer_status from answer where game_session_id=?",String.class,game)).isEqualTo("ACCEPTED_UNSCORED");
            text(ws.get(1),game,1);text(ws.get(2),game,1);advance(q);observer.next("QUESTION_RESULT",game,1);
            assertThat(outcomes(game,1,user)).isEqualTo(1);assertThat(count("select count(*) from answer a join player_session p on p.id=a.player_session_id where a.game_session_id=? and p.user_id=? and a.answer_status='CORRECT' and a.scored_at_ms is not null",game,user)).isEqualTo(1);
            transactions.score(game,1,clock.sample().epochMs());assertThat(outcomes(game,1,user)).isEqualTo(1);
        }finally{release.countDown();}
    }

    @Test void banCommitBeforeAnswerRejectsAtSharedUserFenceWithoutWorkerDeadlock()throws Exception{
        var f=modern(false);var ws=online(f);var admin=admin();long game=startModern(f),user=f.roster().getFirst().id();ready(ws,game);var q=observer.next("QUESTION_START",game,1);
        var saved=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var ban=pool.submit(()->tx().execute(s->{var c=direct(admin,user,"INDEFINITE",request(0,null));saved.countDown();await(release);return c;}));await(saved);
            var answer=pool.submit(()->transactions.acceptTyped(game,user,1,null,new TypedAnswer("answer",null),q.deadlineEpochMs()-20000+1,1));assertThat(answer.isDone()).isFalse();release.countDown();ban.get(5,TimeUnit.SECONDS);
            assertThatThrownBy(()->answer.get(5,TimeUnit.SECONDS)).hasRootCauseInstanceOf(AuthFailure.class);assertThat(outcomes(game,1,user)).isZero();
            advance(q);observer.next("QUESTION_RESULT",game,1);assertThat(outcomes(game,1,user)).isEqualTo(1);
        }finally{release.countDown();}
    }

    @Test void multiplePreBanSessionsStayRevokedAfterUnbanAndOldEffectDoesNotKillFreshSession()throws Exception{
        var user=account();var old1=new Client(user);var old2=new Client(user);var oldWire=new Wire(user);var admin=admin();
        var receipt=apply(admin,user.id(),"TEMP",request(0,60));long action=receipt.path("actionId").asLong();
        for(var c:List.of(old1,old2,oldWire.client))assertThat(c.call("GET","/api/quizzes",null).statusCode()).isEqualTo(403);
        assertThatThrownBy(()->new Client(user)).hasMessageContaining("403");
        apply(admin,user.id(),"UNBAN",request(1,null));
        for(var c:List.of(old1,old2,oldWire.client))assertThat(c.call("GET","/api/auth/me",null).statusCode()).isEqualTo(401);
        var fresh=fresh(user);var newClient=new Client(fresh);var newWire=new Wire(fresh);
        dispatch(action,"SESSIONS");dispatch(action,"SESSIONS");
        assertThat(newClient.call("GET","/api/auth/me",null).statusCode()).isEqualTo(200);assertThat(newWire.closed.isDone()).isFalse();
        assertThat(newWire.response(envelope("SUBSCRIBE_ROOM","ROOM",Long.MAX_VALUE,null,Map.of())).path("kind").asText()).isEqualTo("ERROR");
    }

    @Test void tempTransitionsVersionConflictNoopAndOldReplayNeverReapply()throws Exception{
        var user=account();var admin=admin();var first=request(0,1);var r1=apply(admin,user.id(),"TEMP",first);long expiry=r1.path("committedUntilMs").asLong();assertThat(expiry-r1.path("effectiveAtMs").asLong()).isEqualTo(60000);
        var replay=apply(admin,user.id(),"TEMP",first);assertThat(replay.path("replayed").asBoolean()).isTrue();assertThat(replay.path("committedUntilMs").asLong()).isEqualTo(expiry);
        assertThat(admin.client().call("POST",path(user.id(),"TEMP"),new SanctionRequest(first.requestId(),0L,2,"Other payload")).statusCode()).isEqualTo(409);
        assertThat(admin.client().call("POST",path(user.id(),"INDEFINITE"),request(0,null)).body()).contains("STALE_VERSION");
        var r2=apply(admin,user.id(),"TEMP",request(1,5));assertThat(r2.path("committedUntilMs").asLong()-r2.path("effectiveAtMs").asLong()).isEqualTo(300000);
        apply(admin,user.id(),"INDEFINITE",request(2,null));assertThat(admin.client().call("POST",path(user.id(),"TEMP"),request(3,1)).body()).contains("BAN_DOWNGRADE_FORBIDDEN");
        assertThat(apply(admin,user.id(),"INDEFINITE",request(3,null)).path("committedVersion").asLong()).isEqualTo(3);
        apply(admin,user.id(),"UNBAN",request(3,null));var old=apply(admin,user.id(),"TEMP",first);assertThat(old.path("current").path("kind").asText()).isEqualTo("NONE");assertThat(old.path("current").path("version").asLong()).isEqualTo(4);
        assertThat(apply(admin,user.id(),"UNBAN",request(4,null)).path("result").asText()).isEqualTo("NO_CHANGE");assertThat(accountAccess.state(user.id()).epoch()).isEqualTo(3);
    }

    @Test void expiredTempAllowsNewLoginWithoutCronButNeverRevivesOldSession()throws Exception{
        var user=account();var old=new Client(user);var admin=admin();apply(admin,user.id(),"TEMP",request(0,1));
        jdbc.update("update app_user set banned_until_ms=? where id=?",System.currentTimeMillis()-1,user.id());
        assertThat(old.call("GET","/api/auth/me",null).statusCode()).isEqualTo(401);assertThat(new Client(fresh(user)).call("GET","/api/auth/me",null).statusCode()).isEqualTo(200);
        assertThat(apply(admin,user.id(),"UNBAN",request(1,null)).path("result").asText()).isEqualTo("NO_CHANGE");
    }

    @Test void roleCsrfAdminSelfAndDurationGuardsAreServerEnforced()throws Exception{
        var user=account();var caller=new Client(account());var admin=admin();
        assertThat(caller.call("POST",path(user.id(),"TEMP"),request(0,1)).statusCode()).isEqualTo(403);
        var original=admin.client().csrf;admin.client().csrf="invalid";assertThat(admin.client().call("POST",path(user.id(),"INDEFINITE"),request(0,null)).statusCode()).isEqualTo(403);admin.client().csrf=original;
        assertThat(admin.client().call("POST",path(admin.account().id(),"INDEFINITE"),request(0,null)).body()).contains("BAN_ADMIN_FORBIDDEN");
        assertThat(admin.client().call("POST",path(user.id(),"TEMP"),request(0,null)).statusCode()).isEqualTo(400);
        assertThat(admin.client().call("POST",path(user.id(),"TEMP"),request(0,0)).statusCode()).isEqualTo(400);
        assertThat(admin.client().call("POST",path(user.id(),"UNBAN"),request(0,1)).statusCode()).isEqualTo(400);
        assertThat(admin.client().call("POST",path(Long.MAX_VALUE,"INDEFINITE"),request(0,null)).statusCode()).isEqualTo(404);
        assertThat(admin.client().call("POST","/api/admin/rooms/"+Long.MAX_VALUE+"/close-orphan",new OrphanCloseRequest(UUID.randomUUID().toString(),0L,"Missing target")).statusCode()).isEqualTo(404);
        assertThat(count("select count(*) from admin_account_action where target_user_id=?",user.id())).isZero();
    }

    @Test void rollbackDoesNotLeaveBanIntentAuditEpochReleaseOrFence()throws Exception{
        var f=fixture();long game=start(f);var admin=admin();long user=f.roster().getFirst().id();var request=request(0,null);
        assertThatThrownBy(()->tx().execute(s->{direct(admin,user,"INDEFINITE",request);throw new IllegalStateException("rollback injection");})).isInstanceOf(IllegalStateException.class);
        assertThat(accountAccess.state(user).version()).isZero();assertThat(count("select count(*) from admin_account_action where target_user_id=?",user)).isZero();
        assertThat(count("select count(*) from account_effect_intent where user_id=?",user)).isZero();assertThat(count("select count(*) from user_active_game where user_id=? and game_session_id=?",user,game)).isEqualTo(1);
        assertThat(accountAccess.beginSend(user,0)).isTrue();assertThat(runtime.snapshot(game,user)).isNotNull();assertThat(apply(admin,user,"INDEFINITE",request).path("committedVersion").asLong()).isEqualTo(1);
    }

    @Test void legalReleaseBeforeGameYThenOldMembershipAndSessionIntentsCannotRetargetYOrG2()throws Exception{
        var f=fixture();long x=start(f);var target=f.roster().getFirst();var admin=admin();var oldRequest=request(0,null);var ban=apply(admin,target.id(),"INDEFINITE",oldRequest);long action=ban.path("actionId").asLong();
        apply(admin,target.id(),"UNBAN",request(1,null));var user=fresh(target);
        var hostY=account();var yRoom=room(hostY,f.quiz(),List.of(user,account(),account()));
        assertThatThrownBy(()->operations.start(hostY.auth(),yRoom.id(),request(yRoom),()->{})).isInstanceOf(GameFailure.class).hasMessage("USER_ACTIVE_GAME");
        dispatch(action,"GAME"); // Legitimate release X and g1 detach before START Y.
        long y=operations.start(hostY.auth(),yRoom.id(),request(yRoom),()->{}).gameSessionId();gameIds.add(y);installed.add(y);
        observer.next("DECISION_STARTED",y,1);assertThat(count("select game_session_id from user_active_game where user_id=?",user.id())).isEqualTo(y);
        var freshClient=new Client(user);dispatch(action,"SESSIONS");dispatch(action,"MEMBERSHIP");assertThat(freshClient.call("GET","/api/auth/me",null).statusCode()).isEqualTo(200);
        jdbc.update("update account_effect_intent set state='PENDING',completed_at_ms=null where action_id=? and effect_type='GAME'",action);dispatch(action,"GAME");
        assertThat(count("select game_session_id from user_active_game where user_id=?",user.id())).isEqualTo(y);
        assertThat(apply(admin,user.id(),"INDEFINITE",oldRequest).path("current").path("kind").asText()).isEqualTo("NONE");
        runtime.interrupt(x).get(5,TimeUnit.SECONDS);assertThat(count("select game_session_id from user_active_game where user_id=?",user.id())).isEqualTo(y);
        runtime.interrupt(y).get(5,TimeUnit.SECONDS); // Release Y before legal rejoin g2 in WAITING X.
        var joined=rooms.join(user.id(),f.room().id(),f.room().roomCode(),Participation.PLAYER);
        assertThat(count("select membership_generation from room_member where room_id=? and user_id=?",f.room().id(),user.id())).isEqualTo(2);
        // Simulate an effect not acknowledged across a crash, without inventing/releasing any occupied slot.
        jdbc.update("update account_effect_intent set state='PENDING',completed_at_ms=null where action_id=? and effect_type in ('GAME','MEMBERSHIP','SESSIONS')",action);
        for(String type:List.of("GAME","MEMBERSHIP","SESSIONS"))dispatch(action,type);
        assertThat(rooms.isJoined(user.id(),f.room().id())).isTrue();assertThat(count("select membership_generation from room_member where room_id=? and user_id=?",f.room().id(),user.id())).isEqualTo(2);
        assertThat(freshClient.call("GET","/api/auth/me",null).statusCode()).isEqualTo(200);assertThatThrownBy(()->transactions.read(x,user.id())).isInstanceOf(GameFailure.class).hasMessage("GAME_KICKED");
    }

    @Test void bannedHostCannotCloseActiveGameAndAdminOnlyClosesAuditedOrphanWaiting()throws Exception{
        var f=fixture();long game=start(f);var admin=admin();var peer=new Client(f.roster().getFirst());var host=new Client(f.host());var ban=apply(admin,f.host().id(),"INDEFINITE",request(0,null));
        assertThat(host.call("GET","/api/rooms/"+f.room().id(),null).statusCode()).isEqualTo(403);
        var close=new OrphanCloseRequest(UUID.randomUUID().toString(),rooms.getRetainedForHost(f.host().id(),f.room().id()).revision(),"Close orphan fixture");String endpoint="/api/admin/rooms/"+f.room().id()+"/close-orphan";
        assertThat(admin.client().call("POST",endpoint,close).body()).contains("ORPHAN_WAITING_REQUIRED");assertThat(runtime.snapshot(game,f.roster().getFirst().id()).status()).isEqualTo(GameStatus.ACTIVE);
        dispatch(ban.path("actionId").asLong(),"GAME");assertThat(runtime.snapshot(game,f.roster().getFirst().id()).status()).isEqualTo(GameStatus.ACTIVE);
        runtime.interrupt(game).get(5,TimeUnit.SECONDS);long rev=rooms.getRetainedForHost(f.host().id(),f.room().id()).revision();close=new OrphanCloseRequest(UUID.randomUUID().toString(),rev,"Close orphan fixture");
        assertThat(admin.client().call("GET","/api/admin/orphan-rooms",null).body()).contains("HOST_BANNED");assertThat(admin.client().call("POST",endpoint,close).statusCode()).isEqualTo(200);
        assertThat(admin.client().call("POST",endpoint,close).body()).contains("\"replayed\":true");assertThat(count("select count(*) from admin_orphan_close_action where room_id=?",f.room().id())).isEqualTo(1);
        assertThat(peer.call("GET","/api/auth/me",null).statusCode()).isEqualTo(200);assertThat(peer.call("GET","/api/games/history/"+game,null).statusCode()).isEqualTo(200);
        var normal=fixture();var noBan=new OrphanCloseRequest(UUID.randomUUID().toString(),normal.room().revision(),"Offline is not ban");assertThat(admin.client().call("POST","/api/admin/rooms/"+normal.room().id()+"/close-orphan",noBan).body()).contains("HOST_STILL_AVAILABLE");
    }

    @Test void twoAdminsWithSameVersionOnlyOneActionCommits()throws Exception{
        var user=account();var one=admin();var two=admin();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{await(gate);return one.client().call("POST",path(user.id(),"TEMP"),request(0,60));});
            var b=pool.submit(()->{await(gate);return two.client().call("POST",path(user.id(),"INDEFINITE"),request(0,null));});gate.countDown();
            assertThat(List.of(a.get(5,TimeUnit.SECONDS).statusCode(),b.get(5,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);assertThat(accountAccess.state(user.id()).version()).isEqualTo(1);assertThat(count("select count(*) from admin_account_action where target_user_id=?",user.id())).isEqualTo(1);
        }
    }

    @Test void banCookieCanLoadStaticAndUseSharedLoginAgainAfterUnbanWithoutWaitingForEffects()throws Exception{
        var user=account();var client=new Client(user);var admin=admin();apply(admin,user.id(),"INDEFINITE",request(0,null));
        assertThat(client.call("GET","/app.js",null).statusCode()).isEqualTo(200);assertThat(client.call("GET","/styles.css",null).statusCode()).isEqualTo(200);
        client.token();assertThat(client.call("POST","/api/auth/login",Map.of("username",user.name(),"password",PASSWORD)).body()).contains("ACCOUNT_INDEFINITE_BANNED");
        apply(admin,user.id(),"UNBAN",request(1,null));client.token();assertThat(client.call("POST","/api/auth/login",Map.of("username",user.name(),"password",PASSWORD)).statusCode()).isEqualTo(200);
        assertThat(client.call("GET","/api/auth/me",null).statusCode()).isEqualTo(200);
    }

    @Test void blockedWriterDoesNotDelayBanAndQueuedPreBanPayloadNeverStartsAfterFence()throws Exception{
        var user=account();var admin=admin();var login=new Client(user);String session=login.cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        var socket=mock(org.springframework.web.socket.WebSocketSession.class);when(socket.getId()).thenReturn("ban-slow-"+user.id());when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());when(socket.getPrincipal()).thenReturn(user.auth());when(socket.isOpen()).thenReturn(true);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var sent=new CopyOnWriteArrayList<String>();
        doAnswer(c->{String value=((org.springframework.web.socket.TextMessage)c.getArgument(0)).getPayload();sent.add(value);if(value.contains("PRE_FENCE_SEND")){entered.countDown();assertThat(release.await(4,TimeUnit.SECONDS)).isTrue();}return null;}).when(socket).sendMessage(any());
        sockets.add(session,socket,user.id());sockets.connected(socket);
        try{
            sockets.send(socket,Map.of("payload","PRE_FENCE_SEND"));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();sockets.send(socket,Map.of("private","QUEUED_PRE_BAN"));
            try(var pool=Executors.newSingleThreadExecutor()){var ban=pool.submit(()->apply(admin,user.id(),"INDEFINITE",request(0,null)));assertThat(ban.get(2,TimeUnit.SECONDS).path("committedVersion").asLong()).isEqualTo(1);assertThat(release.getCount()).isEqualTo(1);}
            release.countDown();verify(socket,timeout(3000)).close(any());assertThat(String.join("\n",sent)).doesNotContain("QUEUED_PRE_BAN");
        }finally{release.countDown();sockets.remove(session,socket);}
    }

    @Test void failedDeferredEffectRetainsDurableIntentAndRetryReleasesOnlyCapturedGame()throws Exception{
        var f=fixture();long game=start(f);var admin=admin();var user=f.roster().getFirst();long action=apply(admin,user.id(),"INDEFINITE",request(0,null)).path("actionId").asLong(),effect=targets(action,"GAME").getFirst();
        var first=new AtomicBoolean(true);doAnswer(c->{if(first.getAndSet(false))throw new org.springframework.dao.TransientDataAccessResourceException("Injected effect outage");return c.callRealMethod();}).when(effects).game(effect);
        assertThatThrownBy(()->reconciler.dispatch(effect)).hasRootCauseInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        assertThat(intents.get(effect).state()).isEqualTo("PENDING");assertThatThrownBy(()->runtime.snapshot(game,user.id())).isInstanceOf(GameFailure.class).hasMessage("GAME_KICKED");
        assertThat(count("select count(*) from user_active_game where user_id=? and game_session_id=?",user.id(),game)).isEqualTo(1);
        reconciler.dispatch(effect);assertThat(intents.get(effect).state()).isEqualTo("DONE");assertThat(count("select count(*) from user_active_game where user_id=?",user.id())).isZero();
    }

    @Test void bannedPlayingHostIsExcludedNextOpenWhileOtherPlayersContinueAndNoActiveOrphanClose()throws Exception{
        var host=account();long quiz=quiz(account(),10);var peers=List.of(account(),account());
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"Playing Host",3,null,Participation.PLAYER,List.of(new RoomStageRequest(GameMode.QUIZ,quiz,10,20000L))))).snapshot();roomIds.add(r.id());long revision=r.revision();
        rooms.open(host.id(),r.id(),revision);for(var peer:peers)rooms.join(peer.id(),r.id(),r.roomCode(),Participation.PLAYER);
        var f=new Fixture(host,List.of(host,peers.get(0),peers.get(1)),quiz,rooms.get(host.id(),r.id()));var ws=online(f);long game=startModern(f);ready(ws,game);var decision=observer.next("DECISION_STARTED",game,1);var admin=admin();
        long action=apply(admin,host.id(),"INDEFINITE",request(0,null)).path("actionId").asLong();dispatch(action,"GAME");advance(decision);observer.next("QUESTION_START",game,1);assertThat(participation(game,1,host.id())).isZero();
        for(var w:ws.subList(1,3))accepted(w.response(command("ANSWER",game,1,Map.of("option","D"))));var result=observer.next("QUESTION_RESULT",game,1);assertThat(result.status()).isEqualTo(GameStatus.ACTIVE);assertThat(result.results()).hasSize(2);
        var close=new OrphanCloseRequest(UUID.randomUUID().toString(),rooms.getRetainedForHost(host.id(),r.id()).revision(),"Must not cancel ACTIVE");assertThat(admin.client().call("POST","/api/admin/rooms/"+r.id()+"/close-orphan",close).body()).contains("ORPHAN_WAITING_REQUIRED");
        assertThat(count("select host_user_id from room where id=?",r.id())).isEqualTo(host.id());assertThat(count("select count(*) from account_effect_intent where action_id=? and effect_type='RESERVATION'",action)).isZero();
    }
}
