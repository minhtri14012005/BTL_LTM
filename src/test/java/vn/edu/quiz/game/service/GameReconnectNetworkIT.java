package vn.edu.quiz.game.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.quiz.realtime.message.command.GameCommand;
import vn.edu.quiz.realtime.session.SessionQueue;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real MySQL/cookie/raw WS. Gates and a controlled clock prove generation and
 * snapshot ordering.
 */
class GameReconnectNetworkIT extends GameNetworkFixture {
    @Test
    void inactiveMemberClosesAsExpiredDuringCommittedBroadcastWithoutInterruptingOtherMembers() throws Exception {
        var f = fixture();
        long id = start(f);
        var inactive = new Wire(f.roster().getFirst());
        reconnect(inactive, id);
        var other = new Wire(f.roster().get(1));
        reconnect(other, id);
        jdbc.update("update app_user set deleted_at_ms=greatest(created_at_ms,?) where id=?",
                System.currentTimeMillis(), f.roster().getFirst().id());
        open(id, 1);
        assertThat(inactive.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4001);
        other.event("QUESTION_START", 1);
        assertThat(other.closed.isDone()).isFalse();
        assertThat(runtime.snapshot(id, f.roster().get(1).id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        assertThat(
                jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?", Integer.class, id))
                .isEqualTo(4);
        assertThat(observer.seen).noneMatch(e -> e.type().equals("GAME_UNAVAILABLE"));
    }

    @MockitoSpyBean
    AuthenticatedSocketRegistry registry;

    JsonNode reconnect(Wire wire, long id) throws Exception {
        var ack = wire.response(envelope("RECONNECT", "GAME", id, null, Map.of()));
        accepted(ack);
        assertThat(ack.path("connectionGeneration").asLong())
                .isEqualTo(wire.next(m -> m.path("type").asText().equals("AUTH_READY")).path("payload")
                        .path("connectionGeneration").asLong());
        assertThat(ack.path("revision").asLong()).isEqualTo(ack.path("payload").path("revision").asLong());
        GameSnapshotAssertions.snapshot(ack.path("payload"), wire.client.account.id());
        return ack;
    }

    void await(CountDownLatch latch) throws Exception {
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void disconnectBeforeAndAfterSpinStarAnswerKeepsResourcesAnswerAndOriginalDeadline() throws Exception {
        var f = fixture();
        long id = start(f);
        var before = new Wire(f.roster().getFirst());
        var initial = reconnect(before, id).path("payload");
        assertThat(initial.path("player").path("score").asInt()).isEqualTo(20);
        assertThat(initial.path("player").path("remainingSpins").asInt()).isEqualTo(1);
        assertThat(initial.path("player").path("starAvailable").asBoolean()).isTrue();
        assertThat(initial.path("question").isNull()).isTrue();
        before.close();
        var spinWire = new Wire(f.roster().getFirst());
        assertThat(reconnect(spinWire, id).path("payload").path("deadlineEpochMs"))
                .isEqualTo(initial.path("deadlineEpochMs"));
        doReturn(SpinEffect.BONUS).when(spins).select(any(), any());
        var spin = command("USE_SPIN", id, 1, Map.of());
        var spinAck = spinWire.response(spin);
        accepted(spinAck);
        spinWire.close();
        var starWire = new Wire(f.roster().getFirst());
        var spun = reconnect(starWire, id).path("payload").path("player");
        assertThat(spun.path("remainingSpins").asInt()).isZero();
        assertThat(spun.path("currentSpin").asText()).isEqualTo("BONUS");
        assertThat(spun.path("remainingSpinPool")).hasSize(5);
        assertThat(starWire.response(spin)).isEqualTo(spinAck);
        var star = command("USE_STAR", id, 1, Map.of());
        var starAck = starWire.response(star);
        accepted(starAck);
        starWire.close();
        var answerWire = new Wire(f.roster().getFirst());
        var starred = reconnect(answerWire, id).path("payload").path("player");
        assertThat(starred.path("starAvailable").asBoolean()).isFalse();
        assertThat(starred.path("starSelected").asBoolean()).isTrue();
        assertThat(answerWire.response(star)).isEqualTo(starAck);
        var opened = open(id, 1);
        scheduler.advance(clock.mono.get() + 10);
        var answer = command("ANSWER", id, 1, Map.of("option", "D"));
        var answerAck = answerWire.response(answer);
        accepted(answerAck);
        answerWire.close();
        var back = new Wire(f.roster().getFirst());
        var snapshot = reconnect(back, id).path("payload");
        assertThat(snapshot.path("deadlineEpochMs").asLong()).isEqualTo(opened.deadlineEpochMs());
        assertThat(snapshot.path("remainingMs").asLong()).isEqualTo(990);
        assertThat(snapshot.path("question").path("options")).hasSize(4);
        assertThat(snapshot.path("question").path("correctAnswer").isNull()).isTrue();
        assertThat(snapshot.path("player").path("alreadyAnswered").asBoolean()).isTrue();
        assertThat(snapshot.path("player").path("selectedOption").asText()).isEqualTo("D");
        assertThat(snapshot.path("results")).isEmpty();
        assertThat(back.response(answer)).isEqualTo(answerAck);
        rejected(back.response(command("ANSWER", id, 1, Map.of("option", "A"))), "ALREADY_ANSWERED");
        // Overview: BONUS + Star Correct = +30; initial 20 becomes 50.
        expire(opened);
        observer.next("QUESTION_RESULT", id, 1);
        assertThat(runtime.snapshot(id, f.roster().getFirst().id()).player().score()).isEqualTo(50);
        assertThat(
                jdbc.queryForObject("select count(*) from player_session where game_session_id=?", Integer.class, id))
                .isEqualTo(3);
        assertThat(
                jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?", Integer.class, id))
                .isEqualTo(4);
        verify(spins, times(1)).select(any(), any());
    }

    @Test
    void offlinePlayerStaysInRosterAndNoAnswerAdvancesPhaseWithoutReset() throws Exception {
        var f = fixture();
        long id = start(f);
        var old = new Wire(f.roster().getFirst());
        reconnect(old, id);
        old.close();
        for (int i = 1; i <= 2; i++) {
            var opened = open(id, i);
            expire(opened);
            decision(id,i + 1);
        }
        var fresh = new Wire(f.roster().getFirst());
        var state = reconnect(fresh, id).path("payload");
        assertThat(state.path("questionIndex").asInt()).isEqualTo(3);
        assertThat(state.path("phase").asText()).isEqualTo("DECISION");
        assertThat(state.path("question").isNull()).isTrue();
        assertThat(state.path("player").path("score").asInt()).isEqualTo(18);
        assertThat(state.path("player").path("remainingSpins").asInt()).isEqualTo(1);
        assertThat(state.path("player").path("starAvailable").asBoolean()).isTrue();
        assertThat(state.path("player").path("winStreak").asInt()).isZero();
        assertThat(state.path("player").path("loseStreak").asInt()).isZero();
        assertThat(state.path("members")).hasSize(4);
        assertThat(answerCount(id)).isEqualTo(6);
    }

    @Test
    void spectatorOutsiderAndOpenPrivacyUseAuthenticatedImmutableGameMembership() throws Exception {
        var f = fixture();
        long id = start(f);
        var host = new Wire(f.host());
        var outsider = new Wire(account());
        var state = reconnect(host, id).path("payload");
        assertThat(state.path("player").isNull()).isTrue();
        assertThat(state.path("members").get(0).path("role").asText()).isEqualTo("HOST");
        assertThat(state.path("members").get(0).path("participation").asText()).isEqualTo("SPECTATOR");
        rejected(outsider.response(envelope("RECONNECT", "GAME", id, null, Map.of())), "FORBIDDEN");
        var opened = open(id, 1);
        state = reconnect(host, id).path("payload");
        assertThat(state.path("question").path("id").asLong()).isEqualTo(opened.question().id());
        assertThat(state.path("question").path("correctAnswer").isNull()).isTrue();
        assertThat(state.toString()).doesNotContain("selectedOption", "remainingSpinPool");
        assertThat(state.has("questions")).isFalse();
        var forged = envelope("RECONNECT", "GAME", id, null, Map.of()).put("userId", f.roster().getFirst().id());
        host.send(forged);
        host.next(m -> m.path("code").asText().equals("INVALID_MESSAGE"));
        assertThat(answerCount(id)).isZero();
    }

    @Test
    void queuedOldGenerationCannotAnswerAndStaleCloseOrOldLogoutCannotRemoveNewSocket() throws Exception {
        var f = fixture();
        var blocked = new Wire(f.roster().getFirst());
        var old = new Wire(f.roster().get(1));
        long id = start(f);
        open(id, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queued = new CountDownLatch(1);
        doAnswer(inv -> {
            entered.countDown();
            await(release);
            return inv.callRealMethod();
        }).when(transactions).accept(eq(id), eq(f.roster().getFirst().id()), eq(1), any(), anyLong(), anyLong());
        var stale = command("ANSWER", id, 1, Map.of("option", "A"));
        doAnswer(inv -> {
            var result = inv.callRealMethod();
            queued.countDown();
            return result;
        }).when(runtime).command(eq(f.roster().get(1).id()),
                argThat((GameCommand c) -> c.requestId().equals(stale.path("requestId").asText())), any(), any());
        try {
            var first = command("ANSWER", id, 1, Map.of("option", "D"));
            blocked.send(first);
            await(entered);
            old.send(stale);
            await(queued);
            var fresh = new Wire(f.roster().get(1));
            assertThat(old.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4002);
            var replaced = old.next(m -> m.path("type").asText().equals("SESSION_REPLACED"));
            assertThat(replaced.path("payload").path("replacementGeneration").asLong())
                    .isGreaterThan(replaced.path("payload").path("connectionGeneration").asLong());
            assertThat(old.client.call("POST", "/api/auth/logout", null).statusCode()).isEqualTo(204);
            release.countDown();
            accepted(blocked.response(first.path("requestId").asText(), 1));
            var state = reconnect(fresh, id).path("payload");
            assertThat(state.path("player").path("alreadyAnswered").asBoolean()).isFalse();
            assertThat(answerCount(id)).isEqualTo(1);
            accepted(fresh.response(stale));
            assertThat(answerCount(id)).isEqualTo(2);
            assertThat(fresh.closed.isDone()).isFalse();
            assertThat(old.messages.stream()
                    .noneMatch(m -> m.path("requestId").asText().equals(stale.path("requestId").asText())
                            && m.path("kind").asText().equals("ACK")))
                    .isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void startedSpinMayCommitAfterReplacementWithOneDrawAndRetryOnNewSocket() throws Exception {
        var f = fixture();
        var old = new Wire(f.roster().getFirst());
        long id = start(f);
        var attempts = new AtomicInteger();
        doReturn(SpinEffect.SAFE).when(spins).select(any(), any());
        doAnswer(inv -> {
            var result = inv.callRealMethod();
            if (attempts.incrementAndGet() == 1)
                throw new TransientDataAccessResourceException("Controlled rollback after SQL");
            return result;
        })
                .when(transactions).useSpin(eq(id), eq(f.roster().getFirst().id()), eq(1), eq(SpinEffect.SAFE));
        var spin = command("USE_SPIN", id, 1, Map.of());
        old.send(spin);
        waitUntil(() -> scheduler.tasks.stream().anyMatch(t -> !t.cancelled().get() && t.delay() == 100));
        assertThat(
                jdbc.queryForObject("select remaining_spins from player_session where game_session_id=? and user_id=?",
                        Integer.class, id, f.roster().getFirst().id()))
                .isEqualTo(1);
        var fresh = new Wire(f.roster().getFirst());
        assertThat(old.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4002);
        scheduler.retry(100);
        var state = reconnect(fresh, id).path("payload").path("player");
        assertThat(state.path("remainingSpins").asInt()).isZero();
        assertThat(state.path("currentSpin").asText()).isEqualTo("SAFE");
        accepted(fresh.response(spin));
        accepted(fresh.response(spin));
        assertThat(attempts.get()).isEqualTo(2);
        verify(spins, times(1)).select(any(), any());
        assertThat(old.messages.stream().noneMatch(m -> m.path("kind").asText().equals("ACK"))).isTrue();
    }

    @Test
    void executingAnswerCommitsOnceEvenWhenAckSocketIsReplaced() throws Exception {
        var f = fixture();
        var old = new Wire(f.roster().getFirst());
        long id = start(f);
        var opened = open(id, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(inv -> {
            var result = inv.callRealMethod();
            entered.countDown();
            await(release);
            return result;
        })
                .when(transactions).accept(eq(id), eq(f.roster().getFirst().id()), eq(1), any(), anyLong(), anyLong());
        var answer = command("ANSWER", id, 1, Map.of("option", "D"));
        scheduler.advance(clock.mono.get() + 7);
        old.send(answer);
        try {
            await(entered);
            var fresh = new Wire(f.roster().getFirst());
            assertThat(old.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4002);
            release.countDown();
            var state = reconnect(fresh, id).path("payload");
            assertThat(state.path("player").path("alreadyAnswered").asBoolean()).isTrue();
            assertThat(state.path("question").path("correctAnswer").isNull()).isTrue();
            assertThat(state.path("deadlineEpochMs").asLong()).isEqualTo(opened.deadlineEpochMs());
            var ack = fresh.response(answer);
            accepted(ack);
            assertThat(fresh.response(answer)).isEqualTo(ack);
            assertThat(ack.toString()).doesNotContain("correctAnswer", "CORRECT", "scoreDelta");
            assertThat(answerCount(id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select answer_time_ms from answer where game_session_id=?", Long.class, id))
                    .isEqualTo(7);
            verify(transactions, times(1)).accept(eq(id), eq(f.roster().getFirst().id()), eq(1), any(), anyLong(),
                    anyLong());
            assertThat(old.messages.stream().noneMatch(m -> m.path("kind").asText().equals("ACK"))).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void reconnectKeepsMomentumRecoveryAndStreakAtAuthoritativeProgress() throws Exception {
        var host = account();
        var roster = List.of(account(), account(), account());
        long quiz = quiz(host, 20);
        var room = room(host, quiz, roster);
        var started = operations.start(host.auth(), room.id(),
                new vn.edu.quiz.game.dto.request.StartGameRequest(UUID.randomUUID().toString(), room.revision(), 20),
                () -> {
                });
        long id = started.gameSessionId();
        gameIds.add(id);
        installed.add(id);
        observer.next("DECISION_STARTED", id, 1);
        var wire = new Wire(roster.getFirst());
        for (int index = 1; index <= 5; index++) {
            var opened = open(id, index);
            accepted(wire.response(command("ANSWER", id, index, Map.of("option", "D"))));
            expire(opened);
            decision(id,index + 1);
        }
        wire.close();
        wire = new Wire(roster.getFirst());
        var momentum = reconnect(wire, id).path("payload").path("player");
        assertThat(momentum.path("score").asInt()).isEqualTo(70);
        assertThat(momentum.path("winStreak").asInt()).isZero();
        assertThat(momentum.path("momentum").asBoolean()).isTrue();
        assertThat(momentum.path("remainingSpins").asInt()).isEqualTo(2);
        for (int index = 6; index <= 11; index++) {
            var opened = open(id, index);
            accepted(wire.response(command("ANSWER", id, index, Map.of("option", index == 6 ? "D" : "A"))));
            expire(opened);
            decision(id,index + 1);
        }
        wire.close();
        wire = new Wire(roster.getFirst());
        var recovery = reconnect(wire, id).path("payload").path("player");
        // Overview: q6 Momentum +13, then five Wrong -4; 70+13-20=63.
        assertThat(recovery.path("score").asInt()).isEqualTo(63);
        assertThat(recovery.path("loseStreak").asInt()).isZero();
        assertThat(recovery.path("momentum").asBoolean()).isFalse();
        assertThat(recovery.path("recovery").asBoolean()).isTrue();
        var opened = open(id, 12);
        accepted(wire.response(command("ANSWER", id, 12, Map.of("option", "A"))));
        expire(opened);
        decision(id,13);
        wire.close();
        wire = new Wire(roster.getFirst());
        var consumed = reconnect(wire, id).path("payload").path("player");
        assertThat(consumed.path("score").asInt()).isEqualTo(62);
        assertThat(consumed.path("loseStreak").asInt()).isEqualTo(1);
        assertThat(consumed.path("recovery").asBoolean()).isFalse();
        assertThat(consumed.path("remainingSpins").asInt()).isEqualTo(2);
        assertThat(consumed.path("starAvailable").asBoolean()).isTrue();
        assertThat(consumed.path("remainingSpinPool")).hasSize(6);
    }

    @Test
    void resultSnapshotIsSentBeforeNextDecisionAndCarriesCommittedResultAndEffects() throws Exception {
        var f = fixture();
        long id = start(f);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queued = new CountDownLatch(1);
        doAnswer(inv -> {
            var result = inv.callRealMethod();
            entered.countDown();
            await(release);
            return result;
        }).when(transactions).score(eq(id), eq(1), anyLong());
        doAnswer(inv -> {
            var result = inv.callRealMethod();
            queued.countDown();
            return result;
        }).when(runtime).reconnect(eq(id), eq(f.roster().getFirst().id()), any(), any());
        try {
            expire(open(id, 1));
            await(entered);
            var wire = new Wire(f.roster().getFirst());
            var request = envelope("RECONNECT", "GAME", id, null, Map.of());
            wire.send(request);
            await(queued);
            release.countDown();
            var ack = wire.response(request.path("requestId").asText(), 1);
            accepted(ack);
            var state = ack.path("payload");
            GameSnapshotAssertions.snapshot(state, wire.client.account.id());
            assertThat(state.path("phase").asText()).isEqualTo("RESULT");
            assertThat(state.path("questionIndex").asInt()).isEqualTo(1);
            assertThat(state.path("question").path("correctAnswer").asText()).isEqualTo("D");
            assertThat(state.path("results")).hasSize(3);
            var result = state.path("results").get(0);
            assertThat(result.path("outcome").asText()).isEqualTo("NO_ANSWER");
            assertThat(result.path("scoreAfter").asInt()).isEqualTo(19);
            for (String field : List.of("winStreak", "loseStreak", "hasMomentumBefore", "hasRecoveryAfter",
                    "momentumConsumed", "recoveryGranted", "playerState"))
                assertThat(result.has(field)).isTrue();
            decision(id,2);
            var next = wire.event("DECISION_STARTED", 2);
            assertThat(next.path("revision").asLong()).isGreaterThan(ack.path("revision").asLong());
            assertThat(wire.messages.indexOf(ack)).isLessThan(wire.messages.indexOf(next));
            assertThat(next.path("payload").path("question").isNull()).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void closedAndScoringSnapshotsHaveNoCorrectnessAndReconnectWaitsForActorBoundary() throws Exception {
        var f = fixture();
        long id = start(f);
        var wire = new Wire(f.roster().getFirst());
        reconnect(wire, id);
        var closed = new CountDownLatch(1);
        var releaseClosed = new CountDownLatch(1);
        var scoring = new CountDownLatch(1);
        var releaseScoring = new CountDownLatch(1);
        doAnswer(inv -> {
            var event = (GameLifecycleEvent) inv.getArgument(0);
            var result = inv.callRealMethod();
            if (event.snapshot().gameSessionId() == id && event.type().equals("QUESTION_CLOSED")) {
                closed.countDown();
                await(releaseClosed);
            }
            if (event.snapshot().gameSessionId() == id && event.type().equals("SCORING_STARTED")) {
                scoring.countDown();
                await(releaseScoring);
            }
            return result;
        }).when(registry).gameChanged(any());
        try {
            expire(open(id, 1));
            await(closed);
            var closedSnapshot = json.readTree(wire.client.call("GET", "/api/games/" + id + "/snapshot", null).body());
            GameSnapshotAssertions.snapshot(closedSnapshot, wire.client.account.id());
            assertThat(closedSnapshot.path("phase").asText()).isEqualTo("QUESTION_CLOSED");
            assertThat(closedSnapshot.path("question").path("correctAnswer").isNull()).isTrue();
            assertThat(closedSnapshot.path("results")).isEmpty();
            releaseClosed.countDown();
            await(scoring);
            var scoringSnapshot = json.readTree(wire.client.call("GET", "/api/games/" + id + "/snapshot", null).body());
            GameSnapshotAssertions.snapshot(scoringSnapshot, wire.client.account.id());
            assertThat(scoringSnapshot.path("phase").asText()).isEqualTo("SCORING");
            assertThat(scoringSnapshot.path("question").path("correctAnswer").isNull()).isTrue();
            assertThat(scoringSnapshot.path("results")).isEmpty();
            releaseScoring.countDown();
            decision(id,2);
            assertThat(reconnect(wire, id).path("payload").path("questionIndex").asInt()).isEqualTo(2);
        } finally {
            releaseClosed.countDown();
            releaseScoring.countDown();
            reset(registry);
        }
    }

    @Test
    void eliminatedAndFinishedReconnectKeepsTraceAndFinalSnapshotWorksAfterQueueTtl() throws Exception {
        var f = fixture();
        var old = new Wire(f.roster().getFirst());
        long id = start(f);
        jdbc.update("update player_session set score=0 where game_session_id=? and user_id=?", id,
                f.roster().getFirst().id());
        var first = open(id, 1);
        var answer = command("ANSWER", id, 1, Map.of("option", "A"));
        var ack = old.response(answer);
        accepted(ack);
        expire(first);
        decision(id,2);
        old.close();
        var fresh = new Wire(f.roster().getFirst());
        var eliminated = reconnect(fresh, id).path("payload").path("player");
        assertThat(eliminated.path("state").asText()).isEqualTo("ELIMINATED");
        assertThat(eliminated.path("score").asInt()).isEqualTo(-4);
        assertThat(eliminated.path("eliminatedQuestionIndex").asInt()).isEqualTo(1);
        assertThat(fresh.response(answer)).isEqualTo(ack);
        rejected(fresh.response(command("USE_SPIN", id, 2, Map.of())), "FORBIDDEN");
        for (int i = 2; i <= 10; i++) {
            expire(open(id, i));
            if(i==10) observer.next("GAME_END",id,10); else decision(id,i+1);
        }
        fresh.close();
        var ended = new Wire(f.roster().getFirst());
        var terminal = reconnect(ended, id).path("payload");
        assertThat(terminal.path("status").asText()).isEqualTo("FINISHED");
        assertThat(terminal.path("endReason").asText()).isEqualTo("COMPLETED");
        assertThat(terminal.path("winners")).hasSize(2);
        assertThat(terminal.path("player").path("score").asInt()).isEqualTo(-4);
        assertThat(terminal.path("question").path("id").asLong())
                .isEqualTo(observer.next("QUESTION_START", id, 10).question().id());
        assertThat(terminal.path("results")).hasSize(2);
        assertThat(terminal.path("remainingMs").asLong()).isEqualTo(1500L);
        assertThat(ended.response(answer)).isEqualTo(ack);
        scheduler.advance(clock.mono.get() + SessionQueue.RETENTION_MS);
        var persisted = reconnect(ended, id).path("payload");
        assertThat(persisted.path("status").asText()).isEqualTo("FINISHED");
        assertThat(persisted.path("player").path("eliminatedQuestionIndex").asInt()).isEqualTo(1);
        assertThat(
                jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?", Integer.class, id))
                .isZero();
    }

    @Test
    void expiredTerminalSnapshotRemainsReadableWhileAnOlderReplayIsStillProcessing() throws Exception {
        var f = fixture();
        var old = new Wire(f.roster().getFirst());
        long id = start(f);
        doReturn(SpinEffect.SAFE).when(spins).select(any(), any());
        var spin = command("USE_SPIN", id, 1, Map.of());
        var ack = old.response(spin);
        accepted(ack);
        runtime.interrupt(id).get(5, TimeUnit.SECONDS);
        observer.next("GAME_END", id, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var parsed = new GameCommand(1, "COMMAND", spin.path("requestId").asText(), "USE_SPIN",
                vn.edu.quiz.realtime.message.common.GameTarget.of(id), 1, spin.path("payload"));
        var pending = runtime.command(f.roster().getFirst().id(), parsed, () -> {
            entered.countDown();
            try {
                await(release);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        try {
            await(entered);
            scheduler.advance(clock.mono.get() + SessionQueue.RETENTION_MS);
            var fresh = new Wire(f.roster().getFirst());
            var snapshot = reconnect(fresh, id).path("payload");
            assertThat(snapshot.path("status").asText()).isEqualTo("FINISHED");
            assertThat(snapshot.path("endReason").asText()).isEqualTo("SERVER_INTERRUPTED");
            assertThat(snapshot.path("runtimeState").asText()).isEqualTo("FINISHED");
            assertThat(snapshot.path("player").path("remainingSpins").asInt()).isZero();
            assertThat(snapshot.path("player").path("currentSpin").asText()).isEqualTo("SAFE");
            release.countDown();
            assertThat(json.writeValueAsString(pending.get(5, TimeUnit.SECONDS))).isEqualTo(ack.toString());
            verify(spins, times(1)).select(any(), any());
        } finally {
            release.countDown();
        }
    }
}
