package vn.edu.multigame.game.service;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.repository.PlayerSessionRepository;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.questionbank.entity.*;
import vn.edu.multigame.questionbank.enums.*;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.questionbank.repository.*;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.service.QuestionBankService;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.dto.response.RoomResponse;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.room.service.*;
import vn.edu.multigame.realtime.session.*;
import vn.edu.multigame.realtime.timer.*;
import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual Task8 lifecycle regression on MySQL. */
class GameLifecycleIT extends GameLifecycleFixture {
    @Test void immutableSnapshotsInitialResourcesAuthorSpectatorAndActualTenQuestionServerLifecycle() throws Exception {
        var f=fixture(); long id=start(f);
        var snapshot=runtime.snapshot(id,f.host().id()); assertThat(snapshot.player()).isNull(); assertThat(snapshot.question()).isNull();
        assertThat(snapshot.config().spinCredits()).isEqualTo(1); assertThat(snapshot.members()).hasSize(4);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isEqualTo(4);
        var replacement=Collections.nCopies(10,new QuestionRequest("Changed quiz",Map.of(Option.A,"newA",Option.B,"newB",Option.C,"newC",Option.D,"newD"),Option.A,null));
        quizService.edit(f.host().auth(),f.quiz(),new EditQuestionBankRequest("New title",Visibility.PRIVATE,0L,replacement));
        for(int index=1;index<=10;index++) {
            var opened=open(id,index); assertThat(opened.question().content()).startsWith("Original question"); assertThat(opened.question().correctAnswer()).isNull();
            var self=runtime.snapshot(id,f.roster().getFirst().id()).player(); assertThat(self.remainingSpins()).isEqualTo(1); assertThat(self.starAvailable()).isTrue();
            scheduler.advance(clock.mono.get()+50);
            for(var p:f.roster()) runtime.acceptAnswer(id,p.id(),index,Option.D).get(5,TimeUnit.SECONDS);
            var result=observer.next("QUESTION_RESULT",id,index); assertThat(result.question().correctAnswer()).isEqualTo(Option.D);
            assertThat(result.results()).extracting(GameSnapshot.Result::outcome).containsOnly(AnswerStatus.CORRECT);
            assertThat(result.player()).isNull();
        }
        var ended=observer.next("GAME_END",id,10); assertThat(ended.endReason()).isEqualTo(EndReason.COMPLETED);
        assertThat(ended.winners()).containsExactlyInAnyOrderElementsOf(f.roster().stream().map(Account::id).toList());
        assertThat(ended.members().stream().filter(m -> m.participation()==Participation.PLAYER).map(GameSnapshot.Member::score)).containsOnly(123);
        assertThat(ended.members().stream().filter(m -> m.participation()==Participation.PLAYER).map(GameSnapshot.Member::totalAnswerTimeMs)).containsOnly(500L);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select status from room where id=?",String.class,f.room().id())).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=?",Integer.class,id)).isEqualTo(30);
    }
    @Test void timerOnlyOfflinePlayersRemainEligibleAndCompleteWithNoAnswer() {
        var f=fixture(); long id=start(f);
        for(int index=1;index<=10;index++) { var opened=open(id,index); expire(opened); observer.next("QUESTION_RESULT",id,index); }
        var ended=observer.next("GAME_END",id,10);
        assertThat(ended.endReason()).isEqualTo(EndReason.COMPLETED);
        assertThat(ended.members().stream().filter(m -> m.score()!=null).map(GameSnapshot.Member::score)).containsOnly(10);
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and answer_status='NO_ANSWER' and received_at_ms is null and selected_option is null and answer_time_ms=1000",Integer.class,id)).isEqualTo(30);
    }
    @Test void concurrentStartSameRoomDifferentRequestsCommitsOneAndRetryReturnsOriginalGame() throws Exception {
        var f=fixture(); var first=request(f.room()); var second=request(f.room());
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            var a=pool.submit(() -> startRace(f,first,ready,go)); var b=pool.submit(() -> startRace(f,second,ready,go));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown(); Object aa=a.get(10,TimeUnit.SECONDS),bb=b.get(10,TimeUnit.SECONDS);
            var success=aa instanceof StartGameResponse?(StartGameResponse)aa:(StartGameResponse)bb; long id=success.gameSessionId(); gameIds.add(id); installed.add(id);
            assertThat(List.of(aa,bb).stream().filter(v -> v instanceof StartGameResponse)).hasSize(1);
            var original=aa instanceof StartGameResponse?first:second;
            assertThat(operations.start(f.host().auth(),f.room().id(),original,() -> {}).gameSessionId()).isEqualTo(id);
            assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,f.room().id())).isEqualTo(1);
            assertThatThrownBy(() -> operations.start(f.host().auth(),f.room().id(),new StartGameRequest(original.requestId(),original.revision(),11),() -> {})).isInstanceOf(RoomFailure.class).hasMessage("INVALID_REQUEST_ID");
        }
    }
    @Test void startValidationUsesRealRosterAndAvailableQuestionCount() {
        var host=account(); var two=List.of(account(),account()); long q=quiz(host,10); var r=room(host,q,two);
        assertThatThrownBy(() -> operations.start(host.auth(),r.id(),request(r),() -> {})).hasMessage("NOT_ENOUGH_PLAYERS");
        var extra=account(); roomOps.execute(extra.auth(),r.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),() -> {},() -> rooms.join(extra.id(),r.id(),r.roomCode(),Participation.PLAYER),(saved,replay) -> {});
        var ready=rooms.get(host.id(),r.id());
        assertThatThrownBy(() -> operations.start(host.auth(),r.id(),new StartGameRequest(UUID.randomUUID().toString(),ready.revision(),11),() -> {})).hasMessage("NOT_ENOUGH_QUESTIONS");
        jdbc.update("update room_member set participation='PLAYER' where room_id=? and user_id=?",r.id(),host.id());
        assertThatThrownBy(() -> operations.start(host.auth(),r.id(),request(ready),() -> {})).hasMessageContaining("Tác giả chỉ được quan sát");
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,r.id())).isZero();
    }
    @Test void startSqlRollbackNeverInstallsRuntimeOrPublishesStartedAndFencesRoom() {
        var f=fixture(); var command=request(f.room());
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("Injected Start rollback after SQL")).when(players).flush();
        assertThatThrownBy(() -> operations.start(f.host().auth(),f.room().id(),command,() -> {})).hasMessage("ROOM_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,f.room().id())).isZero();
        assertThat(jdbc.queryForObject("select status from room where id=?",String.class,f.room().id())).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where user_id=?",Integer.class,f.host().id())).isZero();
        assertThat(observer.seen).isEmpty();
        assertThatThrownBy(() -> operations.start(f.host().auth(),f.room().id(),command,() -> {})).hasMessage("ROOM_UNAVAILABLE");
    }
    @Test void committedStartHandoffFailureRetriesOnlyCompensationAndRetainsReceipt() {
        var f=fixture(); var command=request(f.room()); AtomicInteger calls=new AtomicInteger();
        doThrow(new RejectedExecutionException("Injected handoff failure")).when(runtime).install(any(),any());
        doAnswer(inv -> { if(calls.incrementAndGet()<=2) throw new org.springframework.transaction.CannotCreateTransactionException("Injected cleanup connection failure"); return inv.callRealMethod(); }).when(transactions).interrupt(anyLong(),anyLong());
        assertThatThrownBy(() -> operations.start(f.host().auth(),f.room().id(),command,() -> {})).isInstanceOf(GameFailure.class).hasMessage("SERVICE_UNAVAILABLE");
        long id=jdbc.queryForObject("select id from game_session where room_id=?",Long.class,f.room().id()); gameIds.add(id);
        assertThat(calls.get()).isEqualTo(3); assertThat(observer.next("GAME_END",id,0).endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED);
        assertThat(observer.seen.stream().filter(e -> e.type().equals("GAME_STARTED"))).isEmpty();
        assertThat(operations.start(f.host().auth(),f.room().id(),command,() -> {}).gameSessionId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,f.room().id())).isEqualTo(1);
        assertThat(runtime.snapshot(id,f.host().id()).endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED);
    }
    @Test void queuedAnswerAtDeadlineMinusOneScoresEvenWhenProcessorRunsAfterCloseIngress() throws Exception {
        var f=fixture(); long id=start(f); var opened=open(id,1);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("Blocked Answer"); return inv.callRealMethod(); })
                .when(transactions).accept(eq(id),eq(f.roster().getFirst().id()),eq(1),eq(Option.D),anyLong(),anyLong());
        scheduler.advance(opened.deadlineEpochMs()-clock.epoch-1);
        var early=runtime.acceptAnswer(id,f.roster().getFirst().id(),1,Option.D);
        try {
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            clock.mono.incrementAndGet(); // Exact deadline; timer has not been fired yet.
            var exact=runtime.acceptAnswer(id,f.roster().get(1).id(),1,Option.D);
            scheduler.advance(clock.mono.get());
            clock.mono.incrementAndGet(); var late=runtime.acceptAnswer(id,f.roster().getLast().id(),1,Option.D);
            release.countDown(); early.get(5,TimeUnit.SECONDS);
            assertThatThrownBy(() -> exact.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(GameFailure.class);
            assertThatThrownBy(() -> late.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(GameFailure.class);
            var result=observer.next("QUESTION_RESULT",id,1);
            assertThat(result.results().stream().filter(row -> row.userId()==f.roster().getFirst().id()).findFirst().orElseThrow().outcome()).isEqualTo(AnswerStatus.CORRECT);
            assertThat(jdbc.queryForObject("select answer_time_ms from answer a join player_session p on p.id=a.player_session_id where a.game_session_id=? and p.user_id=?",Long.class,id,f.roster().getFirst().id())).isEqualTo(999L);
            var next=decision(id,2);
            assertThat(next.remainingMs()).isEqualTo(next.config().decisionDurationMs());
        } finally { release.countDown(); }
    }
    @Test void resultPublicationFailureNeverRetriesAlreadyCommittedScore() {
        var f=fixture(); long id=start(f); var opened=open(id,1); observer.failPublication.set("QUESTION_RESULT");
        expire(opened); var ended=observer.next("GAME_END",id,1);
        assertThat(ended.endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED); assertThat(ended.winners()).isEmpty();
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(19);
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=?",Integer.class,id)).isEqualTo(3);
        assertThat(observer.seen.stream().filter(e -> e.type().equals("QUESTION_RESULT") && e.snapshot().gameSessionId()==id)).hasSize(1);
    }
    Object startRace(Fixture f,StartGameRequest request,CountDownLatch ready,CountDownLatch go) throws Exception {
        ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier");
        try { return operations.start(f.host().auth(),f.room().id(),request,() -> {}); } catch(GameFailure conflict) { return conflict.getMessage(); }
    }
    @Test void crossedRoomsWithOnlySharedSpectatorStillHaveOneAtomicWinner() throws Exception {
        var a=fixture(); var hostB=account(); var rosterB=List.of(account(),account(),account()); long q=quiz(hostB,10); var rb=room(hostB,q,rosterB);
        roomOps.execute(a.host().auth(),rb.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",rb.roomCode(),"participation","SPECTATOR"),rb.roomCode(),() -> {},() -> rooms.join(a.host().id(),rb.id(),rb.roomCode(),Participation.SPECTATOR),(s,replay) -> {});
        var b=new Fixture(hostB,rosterB,q,rooms.get(hostB.id(),rb.id()));
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            var aa=pool.submit(() -> startRace(a,request(a.room()),ready,go)); var bb=pool.submit(() -> startRace(b,request(b.room()),ready,go));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown(); Object va=aa.get(10,TimeUnit.SECONDS),vb=bb.get(10,TimeUnit.SECONDS);
            assertThat(List.of(va,vb).stream().filter(v -> v instanceof StartGameResponse)).hasSize(1);
            long id=(va instanceof StartGameResponse s?s:(StartGameResponse)vb).gameSessionId(); gameIds.add(id); installed.add(id);
            assertThat(va instanceof String?va:vb).isEqualTo("USER_ACTIVE_GAME");
            assertThat(jdbc.queryForObject("select count(*) from user_active_game where user_id=?",Integer.class,a.host().id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from player_session where user_id=? and game_session_id=?",Integer.class,a.host().id(),id)).isZero();
        }
    }
    @Test void startRosterCannotBeInterleavedByJoin() throws Exception {
        var f=fixture(); var extra=account(); CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=pool.submit(() -> roomOps.get(f.host().auth(),f.room().id())); // Establish existing boundary first.
            start.get(5,TimeUnit.SECONDS);
            // Hold exactly the shared Room boundary while Start captures/persists the roster.
            RoomBoundary boundary=org.springframework.test.util.ReflectionTestUtils.getField(roomOps,"boundary") instanceof RoomBoundary b?b:null;
            var playing=pool.submit(() -> boundary.read(RoomBoundary.Scope.room(f.room().id()),() -> { held.countDown(); try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException e) { throw new AssertionError(e); } return operations.start(f.host().auth(),f.room().id(),request(f.room()),() -> {}); }));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
            var joining=pool.submit(() -> { try { return roomOps.execute(extra.auth(),f.room().id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",f.room().roomCode(),"participation","PLAYER"),f.room().roomCode(),() -> {},() -> rooms.join(extra.id(),f.room().id(),f.room().roomCode(),Participation.PLAYER),(s,replay) -> {}); } catch(RoomFailure failure) { return failure.code(); } });
            release.countDown(); long id=playing.get(10,TimeUnit.SECONDS).gameSessionId(); gameIds.add(id); installed.add(id);
            assertThat(joining.get(10,TimeUnit.SECONDS)).isEqualTo("INVALID_STATE");
            assertThat(jdbc.queryForObject("select count(*) from game_member where game_session_id=?",Integer.class,id)).isEqualTo(4);
        } finally { release.countDown(); }
    }
    long preparedScoring(Fixture f,int index) {
        var started=transactions.start(f.host().id(),f.room().id(),f.room().revision(),10,clock.sample().epochMs()); long id=started.game().publicView().gameSessionId(); gameIds.add(id);
        if(index==10) jdbc.update("update game_session set current_question_index=9,phase='RESULT' where id=?",id);
        transactions.open(id,index,Phase.DECISION,d -> PhaseWindow.open(index,Phase.DECISION,1,clock.mono.get(),clock.sample().epochMs(),d));
        transactions.open(id,index,Phase.QUESTION_OPEN,d -> PhaseWindow.open(index,Phase.QUESTION_OPEN,2,clock.mono.get(),clock.sample().epochMs(),d));
        transactions.transition(id,index,Phase.QUESTION_OPEN,Phase.QUESTION_CLOSED); transactions.transition(id,index,Phase.QUESTION_CLOSED,Phase.SCORING); return id;
    }
    @Test void twoConcurrentScoringTransactionsNeverDoubleApplyQuestion() throws Exception {
        var f=fixture(); long id=preparedScoring(f,1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            Callable<CommittedGame> scoring=() -> { ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier"); return transactions.score(id,1,clock.sample().epochMs()); };
            var a=pool.submit(scoring); var b=pool.submit(scoring); assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown(); a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
            assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(19);
            assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=?",Integer.class,id)).isEqualTo(3);
        }
    }
    @Test void finalQuestionAllEliminatedOutranksCompletedAndAllRankOneAreWinners() {
        var f=fixture(); long id=preparedScoring(f,10); jdbc.update("update player_session set score=0 where game_session_id=?",id);
        var result=transactions.score(id,10,clock.sample().epochMs()).publicView();
        assertThat(result.endReason()).isEqualTo(EndReason.ALL_ELIMINATED); assertThat(result.winners()).hasSize(3);
        assertThat(result.members().stream().filter(m -> m.score()!=null).map(GameSnapshot.Member::score)).containsOnly(-1);
        assertThat(jdbc.queryForObject("select count(*) from player_session where game_session_id=? and player_state='ELIMINATED' and eliminated_question_index=10",Integer.class,id)).isEqualTo(3);
    }
    @Test void finalQuestionOneSurvivorOutranksCompletedAfterScoringWholeRoster() {
        var f=fixture(); long id=preparedScoring(f,10); jdbc.update("update player_session set score=0 where game_session_id=? and user_id<>?",id,f.roster().getLast().id());
        var result=transactions.score(id,10,clock.sample().epochMs()).publicView();
        assertThat(result.endReason()).isEqualTo(EndReason.ONE_SURVIVOR); assertThat(result.winners()).containsExactly(f.roster().getLast().id());
        assertThat(result.results()).hasSize(3);
    }
    @Test void transientRollbackRetriesTwiceWithoutPublishingPartialScoreThenCommitsOnce() {
        var f=fixture(); long id=start(f); var opened=open(id,1); AtomicInteger calls=new AtomicInteger();
        // answers.flush already executed SQL in this real MySQL transaction. The repository interface
        // has no concrete flush method for callRealMethod; after injection, commit performs the flush.
        doAnswer(inv -> { int attempt=calls.incrementAndGet(); if(attempt<=2) throw new org.springframework.dao.TransientDataAccessResourceException("Injected rollback after SQL flush"); return null; }).when(players).flush();
        expire(opened); scheduler.retry(100);
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(20);
        assertThat(observer.seen.stream().filter(e -> e.type().equals("QUESTION_RESULT") && e.snapshot().gameSessionId()==id)).isEmpty();
        scheduler.retry(300); var result=observer.next("QUESTION_RESULT",id,1);
        assertThat(result.members().stream().filter(m -> m.score()!=null).map(GameSnapshot.Member::score)).containsOnly(19); assertThat(calls.get()).isGreaterThanOrEqualTo(3);
    }
    @Test void exhaustedScoringRollbackBecomesUnavailableAndDurablyInterruptsWithoutWinner() {
        var f=fixture(); long id=start(f); var opened=open(id,1); AtomicInteger calls=new AtomicInteger();
        doAnswer(inv -> { if(calls.incrementAndGet()<=3) throw new org.springframework.dao.TransientDataAccessResourceException("Injected rollback"); return null; }).when(players).flush();
        expire(opened); scheduler.retry(100); scheduler.retry(300);
        var ended=observer.next("GAME_END",id,1); assertThat(ended.endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED); assertThat(ended.winners()).isEmpty();
        assertThat(ended.runtimeState()).isEqualTo("UNAVAILABLE"); assertThat(ended.cleanupPending()).isFalse();
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(20);
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
    }
    @Test void continuedCleanupFailureNotifiesRuntimeErrorWithoutClaimingPersistedGameEnd() {
        var f=fixture(); long id=start(f); var opened=open(id,1);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Injected rollback after flush")).when(players).flush();
        AtomicInteger cleanupCalls=new AtomicInteger();
        doAnswer(inv -> { cleanupCalls.incrementAndGet(); throw new org.springframework.transaction.CannotCreateTransactionException("Injected unavailable cleanup connection"); }).when(transactions).interrupt(eq(id),anyLong());
        expire(opened); observer.next("GAME_UNAVAILABLE",id,1); scheduler.retry(100); scheduler.retry(300);
        waitUntil(() -> cleanupCalls.get()==3);
        var snapshot=runtime.snapshot(id,f.host().id()); assertThat(snapshot.runtimeState()).isEqualTo("UNAVAILABLE"); assertThat(snapshot.cleanupPending()).isTrue();
        assertThat(snapshot.status()).isEqualTo(GameStatus.ACTIVE);
        assertThat(observer.seen.stream().filter(e -> e.type().equals("GAME_END") && e.snapshot().gameSessionId()==id)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isEqualTo(4);
    }
    @Test void interruptionPreservesAcceptedUnscoredAndStartupCleanupReleasesAllOccupancies() throws Exception {
        var f=fixture(); long id=start(f); open(id,1); scheduler.advance(clock.mono.get()+10);
        runtime.acceptAnswer(id,f.roster().getFirst().id(),1,Option.D).get(5,TimeUnit.SECONDS); runtime.interrupt(id).get(5,TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("select answer_status from answer where game_session_id=?",String.class,id)).isEqualTo("ACCEPTED_UNSCORED");
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and scored_at_ms is null and score_delta is null",Integer.class,id)).isEqualTo(1);
        var other=fixture(); var abandoned=transactions.start(other.host().id(),other.room().id(),other.room().revision(),10,clock.sample().epochMs()); long old=abandoned.game().publicView().gameSessionId(); gameIds.add(old);
        assertThat(transactions.cleanupAbandoned(clock.sample().epochMs())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select end_reason from game_session where id=?",String.class,old)).isEqualTo("SERVER_INTERRUPTED");
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,old)).isZero();
    }
    @Test void actualRestAndWebsocketStartShareReceiptSnapshotRightsAndPostCommitEvents() throws Exception {
        var f=fixture(); var host=new Client(f.host()); var player=new Client(f.roster().getFirst()); var outsider=new Client(account());
        var command=request(f.room());
        assertThat(player.call("POST","/api/rooms/"+f.room().id()+"/start",command).statusCode()).isEqualTo(403);
        assertThat(host.call("POST","/api/rooms/"+f.room().id()+"/start",Map.of("requestId",UUID.randomUUID().toString(),"revision",f.room().revision(),"questionCount",9)).statusCode()).isEqualTo(400);
        BlockingQueue<JsonNode> received=new LinkedBlockingQueue<>();
        var ws=host.http.newWebSocketBuilder().header("Origin","http://localhost:8080").buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),new WebSocket.Listener() {
            StringBuilder text=new StringBuilder(); public void onOpen(WebSocket ws) { ws.request(1); }
            public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last) { text.append(data); if(last) { try { received.add(json.readTree(text.toString())); } catch(Exception e) { throw new AssertionError(e); } text.setLength(0); } ws.request(1); return null; }
        }).get(5,TimeUnit.SECONDS);
        try {
            assertThat(received.poll(5,TimeUnit.SECONDS).path("type").asText()).isEqualTo("AUTH_READY");
            ws.sendText(json.writeValueAsString(Map.of("v",1,"kind","COMMAND","requestId",command.requestId(),"type","START_GAME","target",Map.of("kind","ROOM","id",f.room().id()),"questionIndex",json.nullNode(),"payload",Map.of("revision",command.revision(),"questionCount",10))),true).get(5,TimeUnit.SECONDS);
            JsonNode ack=null; long bound=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(ack==null && System.nanoTime()<bound) { var event=received.poll(5,TimeUnit.SECONDS); if(event!=null && event.path("kind").asText().equals("ACK")) ack=event; }
            assertThat(ack).isNotNull(); long id=ack.path("payload").path("gameSessionId").asLong(); gameIds.add(id); installed.add(id);
            observer.next("DECISION_STARTED",id,1);
            var retry=host.call("POST","/api/rooms/"+f.room().id()+"/start",command); assertThat(retry.statusCode()).as(retry.body()).isEqualTo(200); assertThat(json.readTree(retry.body()).path("gameSessionId").asLong()).isEqualTo(id);
            assertThat(outsider.call("GET","/api/games/"+id+"/snapshot",null).statusCode()).isEqualTo(403);
            var privateSnapshot=json.readTree(player.call("GET","/api/games/"+id+"/snapshot",null).body()); assertThat(privateSnapshot.path("player").path("userId").asLong()).isEqualTo(f.roster().getFirst().id()); assertThat(privateSnapshot.path("question").isNull()).isTrue();
            open(id,1); scheduler.advance(clock.mono.get()+10); runtime.acceptAnswer(id,f.roster().getFirst().id(),1,Option.D).get(5,TimeUnit.SECONDS); expire(observer.next("QUESTION_START",id,1));
            observer.next("QUESTION_RESULT",id,1);
            JsonNode result=null; bound=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(result==null && System.nanoTime()<bound) { var event=received.poll(5,TimeUnit.SECONDS); if(event!=null && event.path("type").asText().equals("QUESTION_RESULT")) result=event; }
            assertThat(result).isNotNull(); assertThat(result.path("target").path("kind").asText()).isEqualTo("GAME"); assertThat(result.path("payload").path("player").isNull()).isTrue();
            assertThat(result.path("payload").path("question").path("correctAnswer").asText()).isEqualTo("D"); assertThat(result.toString()).doesNotContain("passwordHash","password_hash","remainingSpinPool");
        } finally { ws.sendClose(WebSocket.NORMAL_CLOSURE,"done").get(5,TimeUnit.SECONDS); }
    }
}
