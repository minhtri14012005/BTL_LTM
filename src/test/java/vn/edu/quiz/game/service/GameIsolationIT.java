package vn.edu.quiz.game.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import vn.edu.quiz.game.dto.response.StartGameResponse;
import vn.edu.quiz.game.enums.SpinEffect;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Two real MySQL games and raw WS streams. No sleep-based race or replacement engine. */
class GameIsolationIT extends GameNetworkFixture {
    void occupancies(long game,int expected) {
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,game)).isEqualTo(expected);
    }
    void roomState(Fixture f,String state) {
        assertThat(jdbc.queryForObject("select status from room where id=?",String.class,f.room().id())).isEqualTo(state);
    }
    void await(CountDownLatch latch) throws Exception { assertThat(latch.await(10,TimeUnit.SECONDS)).isTrue(); }

    @Test void concurrentRoomsKeepScoringTransactionsResourcesStreamsAndCleanupIsolated() throws Exception {
        var a=fixture();var b=fixture();var hostA=new Wire(a.host());var hostB=new Wire(b.host());
        hostA.subscribe(a);hostB.subscribe(b);
        long gameA,gameB;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
            Callable<StartGameResponse> startA=() -> {ready.countDown();await(go);return operations.start(a.host().auth(),a.room().id(),request(a.room()),() -> {});};
            Callable<StartGameResponse> startB=() -> {ready.countDown();await(go);return operations.start(b.host().auth(),b.room().id(),request(b.room()),() -> {});};
            var first=pool.submit(startA);var second=pool.submit(startB);await(ready);go.countDown();
            gameA=first.get(10,TimeUnit.SECONDS).gameSessionId();gameIds.add(gameA);installed.add(gameA);
            gameB=second.get(10,TimeUnit.SECONDS).gameSessionId();gameIds.add(gameB);installed.add(gameB);
        }
        assertThat(gameA).isNotEqualTo(gameB);observer.next("DECISION_STARTED",gameA,1);observer.next("DECISION_STARTED",gameB,1);
        occupancies(gameA,4);occupancies(gameB,4);roomState(a,"ACTIVE");roomState(b,"ACTIVE");
        var playerA=new Wire(a.roster().getFirst());var playerB=new Wire(b.roster().getFirst());
        doReturn(SpinEffect.BONUS).when(spins).select(any(),any());
        accepted(playerA.response(command("USE_SPIN",gameA,1,Map.of())));accepted(playerA.response(command("USE_STAR",gameA,1,Map.of())));
        accepted(playerB.response(command("USE_STAR",gameB,1,Map.of())));
        open(gameA,1);var openedB=observer.next("QUESTION_START",gameB,1);scheduler.advance(clock.mono.get()+17);
        var answerA=command("ANSWER",gameA,1,Map.of("option","D"));var answerB=command("ANSWER",gameB,1,Map.of("option","D"));
        answerB.put("requestId",answerA.path("requestId").asText()); // Distinct User/Game scopes may reuse UUID.
        var ackA=playerA.response(answerA);var ackB=playerB.response(answerB);accepted(ackA);accepted(ackB);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var scoringA=new ThreadLocal<Boolean>();
        doAnswer(inv -> {scoringA.set(true);try{return inv.callRealMethod();}finally{scoringA.remove();}}).when(transactions).score(eq(gameA),eq(1),anyLong());
        // answers.flush() already sends the entire persistence context to MySQL. Repository flush
        // is an interface method on this spy; commit performs the remaining normal flush.
        doAnswer(inv -> {if(Boolean.TRUE.equals(scoringA.get())){entered.countDown();await(release);}return null;}).when(players).flush();
        try {
            expire(openedB);await(entered);observer.next("QUESTION_RESULT",gameB,1);decision(gameB,2);
            // A flushed its tentative SQL but has not committed. B progresses through the same worker/pool infrastructure.
            assertThat(runtime.snapshot(gameA,a.host().id()).members().stream().filter(m -> m.score()!=null).map(m -> m.score())).containsOnly(20);
            assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,gameA)).containsOnly(20);
            assertThat(answerCount(gameA)).isEqualTo(1);assertThat(answerCount(gameB)).isEqualTo(3);
            assertThat(runtime.snapshot(gameB,b.roster().getFirst().id()).player().score()).isEqualTo(45); // Star-only correct +25.
            assertThat(runtime.snapshot(gameB,b.roster().getFirst().id()).player().remainingSpins()).isEqualTo(1);
            rejected(playerA.response(command("ANSWER",gameB,2,Map.of("option","D"))),"FORBIDDEN");
            assertThat(hostA.client.call("GET","/api/games/history/"+gameB,null).statusCode()).isEqualTo(403);
            var cancel=envelope("CANCEL_GAME","GAME",gameB,null,Map.of());accepted(hostB.response(cancel));hostB.event("GAME_END",2);
            occupancies(gameB,0);roomState(b,"WAITING");occupancies(gameA,4);roomState(a,"ACTIVE");
            assertThat(playerB.response(answerB)).isEqualTo(ackB); // Room B terminal receipt remains independently routable.
            assertThat(hostA.messages.stream().filter(m -> m.path("target").path("kind").asText().equals("GAME"))).allMatch(m -> m.path("target").path("id").asLong()==gameA);
            assertThat(hostB.messages.stream().filter(m -> m.path("target").path("kind").asText().equals("GAME"))).allMatch(m -> m.path("target").path("id").asLong()==gameB);
            scheduler.advance(clock.mono.get()+200);release.countDown();observer.next("QUESTION_RESULT",gameA,1);
            var nextA=decision(gameA,2);assertThat(nextA.remainingMs()).isEqualTo(7000L);
            assertThat(runtime.snapshot(gameA,a.roster().getFirst().id()).player().score()).isEqualTo(50); // BONUS+Star correct +30.
            assertThat(runtime.snapshot(gameA,a.roster().getFirst().id()).player().remainingSpins()).isZero();
            assertThat(playerA.response(answerA)).isEqualTo(ackA);assertThat(observer.seen).noneMatch(e -> e.type().equals("GAME_UNAVAILABLE"));
        } finally {release.countDown();}
    }

    @Test void exhaustedScoringInOneRoomDoesNotFenceOrReleaseAnotherActiveGame() throws Exception {
        var a=fixture();var b=fixture();long gameA=start(a),gameB=start(b);
        var scoringA=new ThreadLocal<Boolean>();
        doAnswer(inv -> {scoringA.set(true);try{return inv.callRealMethod();}finally{scoringA.remove();}}).when(transactions).score(eq(gameA),eq(1),anyLong());
        doAnswer(inv -> {if(Boolean.TRUE.equals(scoringA.get()))throw new TransientDataAccessResourceException("Room A test rollback after real SQL flush");return null;}).when(players).flush();
        open(gameA,1);var openedB=observer.next("QUESTION_START",gameB,1);expire(openedB);
        // Advance finite DB retries before advancing the shared clock by the RESULT window.
        scheduler.retry(100);scheduler.retry(300);decision(gameB,2);
        var endedA=observer.next("GAME_END",gameA,1);assertThat(endedA.endReason().name()).isEqualTo("SERVER_INTERRUPTED");assertThat(endedA.hasOfficialWinner()).isFalse();
        occupancies(gameA,0);roomState(a,"WAITING");occupancies(gameB,4);roomState(b,"ACTIVE");
        assertThat(answerCount(gameA)).isZero();assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,gameA)).containsOnly(20);
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,gameB)).containsOnly(19);
        var opened=open(gameB,2);var playerB=new Wire(b.roster().getFirst());accepted(playerB.response(command("ANSWER",gameB,2,Map.of("option","D"))));expire(opened);decision(gameB,3);
        assertThat(runtime.snapshot(gameB,b.roster().getFirst().id()).player().score()).isEqualTo(29);
        assertThat(observer.seen.stream().filter(e -> e.type().equals("GAME_UNAVAILABLE"))).allMatch(e -> e.snapshot().gameSessionId()==gameA);
        verify(transactions,times(3)).score(eq(gameA),eq(1),anyLong());
    }
}
