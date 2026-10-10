package vn.edu.multigame.realtime.session;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.multigame.game.enums.Phase;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.game.runtime.QuestionCloseGate;
import vn.edu.multigame.realtime.timer.PhaseTimer;
import vn.edu.multigame.realtime.timer.ServerClock;
import vn.edu.multigame.realtime.timer.TimerScheduler;
import static org.assertj.core.api.Assertions.*;

/** Controlled clock/latches and a tiny handler. No lifecycle, DB transaction or full-game evidence. */
class SessionQueueTest {
    private record Command(String label, Consumer<Ingress<Command>> action) {}
    private static final class Clock implements ServerClock {
        final AtomicReference<Sample> now = new AtomicReference<>(new Sample(0, 1_000_000));
        void at(long mono) { at(mono, 1_000_000 + mono); }
        void at(long mono, long epoch) { now.set(new Sample(mono, epoch)); }
        @Override public Sample sample() { return now.get(); }
    }
    private static final class Scheduler implements TimerScheduler {
        final class Task implements Ticket {
            final Runnable callback;
            final long delay;
            boolean cancelled;
            Task(Runnable callback, long delay) { this.callback = callback; this.delay = delay; }
            // Intentionally fire even after cancellation: already-dispatched callbacks must be harmless.
            void fire() { callback.run(); }
            @Override public void cancel() { cancelled = true; }
        }
        final List<Task> tasks = new CopyOnWriteArrayList<>();
        boolean closed;
        @Override public Ticket schedule(Runnable callback, long delayMs) {
            Task task = new Task(callback, delayMs); tasks.add(task); return task;
        }
        @Override public void close() { closed = true; }
    }
    private static final class Fixture implements AutoCloseable {
        final Clock clock = new Clock();
        final Scheduler scheduler = new Scheduler();
        final List<String> trace = new CopyOnWriteArrayList<>();
        final List<Ingress<?>> ingress = new CopyOnWriteArrayList<>();
        final SessionQueue<Command> queue;
        final SessionQueue.SessionKey key;
        Consumer<Ingress<PhaseTimer>> onTimer = timer -> {};
        Fixture() { this(4, 64, 2); }
        Fixture(int sessions, int commands, int batch) {
            queue = new SessionQueue<>(clock, scheduler, 2, sessions, commands, batch);
            key = queue.register(10, handler());
        }
        SessionQueue.Handler<Command> handler() {
            return new SessionQueue.Handler<>() {
                public void onCommand(Ingress<Command> command) {
                    ingress.add(command); trace.add(command.value().label()); command.value().action().accept(command);
                }
                public void onTimer(Ingress<PhaseTimer> timer) {
                    ingress.add(timer); trace.add("timer"); onTimer.accept(timer);
                }
            };
        }
        SessionQueue.Submission<Command> submit(String label, Consumer<Ingress<Command>> action) {
            return queue.submit(key, new Command(label, action));
        }
        void run(String label, Consumer<Ingress<Command>> action) throws Exception { done(submit(label, action)); }
        void barrier() throws Exception { run("barrier", command -> {}); }
        PhaseWindow open(int question, Phase phase, long token, long duration) throws Exception {
            AtomicReference<PhaseWindow> result = new AtomicReference<>();
            run("open", command -> {
                var now = clock.sample();
                var window = PhaseWindow.open(question, phase, token, now.monotonicMs(), now.epochMs(), duration);
                result.set(window); queue.armTimer(key, window);
            });
            return result.get();
        }
        @Override public void close() { queue.close(); }
    }
    private static void done(SessionQueue.Submission<?> submission) throws Exception { submission.processed().get(5, TimeUnit.SECONDS); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test barrier timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    @ParameterizedTest @CsvSource({"99,true", "100,false", "101,false"})
    void deadlineMinusOneExactlyAndPlusOneEvenWithoutTimer(long receivedAt, boolean expected) throws Exception {
        try (var f = new Fixture()) {
            PhaseWindow window = f.open(1, Phase.QUESTION_OPEN, 1, 100);
            f.clock.at(receivedAt);
            AtomicReference<Boolean> accepted = new AtomicReference<>();
            var answer = f.submit("answer", command -> accepted.set(window.accepts(command.receivedAtMs())));
            done(answer);
            assertThat(accepted.get()).isEqualTo(expected);
            assertThat(answer.ingress().receivedAtMs()).isEqualTo(receivedAt);
            assertThat(answer.ingress().serverTimeMs()).isEqualTo(1_000_000 + receivedAt);
            assertThat(f.trace).doesNotContain("timer");
        }
    }

    @Test void preDeadlineAnswerCannotBeOvertakenWhileProcessorBlocked() throws Exception {
        try (var f = new Fixture()) {
            var window = f.open(1, Phase.QUESTION_OPEN, 1, 100);
            var gate = new QuestionCloseGate(Set.of(1L, 2L));
            AtomicInteger closes = new AtomicInteger();
            f.onTimer = timer -> { if (gate.close()) closes.incrementAndGet(); };
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var block = f.submit("block", command -> { entered.countDown(); await(release); });
            await(entered);
            try {
                f.clock.at(99);
                var answer = f.submit("answer", command -> {
                    assertThat(gate.isClosed()).isFalse();
                    assertThat(window.accepts(command.receivedAtMs())).isTrue();
                    assertThat(gate.recordValidAnswer(1)).isTrue();
                });
                f.clock.at(100);
                f.scheduler.tasks.getFirst().fire();
                var barrier = f.submit("barrier", command -> {});
                assertThat(answer.processed()).isNotDone();
                release.countDown(); done(block); done(answer); done(barrier);
                assertThat(f.trace).containsExactly("open", "block", "answer", "timer", "barrier");
                assertThat(f.ingress).extracting(Ingress::sequence).containsExactly(1L, 2L, 3L, 4L, 5L);
                assertThat(closes).hasValue(1);
            } finally { release.countDown(); }
        }
    }

    @Test void sameSessionNeverRunsTwoHandlersAndPreservesAdmissionOrder() throws Exception {
        try (var f = new Fixture()) {
            AtomicInteger active = new AtomicInteger();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var first = f.submit("first", command -> { assertThat(active.incrementAndGet()).isEqualTo(1); entered.countDown(); await(release); active.decrementAndGet(); });
            await(entered);
            try {
                var second = f.submit("second", command -> { assertThat(active.incrementAndGet()).isEqualTo(1); active.decrementAndGet(); });
                var third = f.submit("third", command -> { assertThat(active.incrementAndGet()).isEqualTo(1); active.decrementAndGet(); });
                release.countDown(); done(first); done(second); done(third);
                assertThat(f.trace).containsExactly("first", "second", "third");
                assertThat(f.ingress).extracting(Ingress::sequence).containsExactly(1L, 2L, 3L);
            } finally { release.countDown(); }
        }
    }

    @Test void slowSessionADoesNotBlockSessionBWithFreeWorker() throws Exception {
        try (var f = new Fixture()) {
            var keyB = f.queue.register(20, f.handler());
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var a = f.submit("A", command -> { entered.countDown(); await(release); });
            await(entered);
            try {
                var b = f.queue.submit(keyB, new Command("B", command -> {}));
                done(b); assertThat(a.processed()).isNotDone();
                assertThat(b.ingress().gameSessionId()).isEqualTo(20); assertThat(b.ingress().sequence()).isEqualTo(1);
            } finally { release.countDown(); }
            done(a);
        }
    }

    @Test void finiteBatchYieldsWorkerToAnotherReadySession() throws Exception {
        try (var f = new Fixture(4, 64, 1)) {
            var occupied = f.queue.register(20, f.handler());
            var ready = f.queue.register(30, f.handler());
            CountDownLatch bothStarted = new CountDownLatch(2), releaseA = new CountDownLatch(1), releaseOther = new CountDownLatch(1);
            var a = f.submit("A-first", command -> { bothStarted.countDown(); await(releaseA); });
            var other = f.queue.submit(occupied, new Command("occupied", command -> { bothStarted.countDown(); await(releaseOther); }));
            await(bothStarted);
            try {
                var aNext = f.submit("A-next", command -> {});
                var b = f.queue.submit(ready, new Command("B-ready", command -> {}));
                assertThat(b.processed()).isNotDone(); releaseA.countDown(); done(a); done(b); done(aNext);
                assertThat(f.trace).containsSubsequence("A-first", "B-ready", "A-next");
                assertThat(other.processed()).isNotDone();
            } finally { releaseA.countDown(); releaseOther.countDown(); }
            done(other);
        }
    }
    @Test void deferredOperationHoldsLaterIngressAndReleasesWorkerUntilContinuation() throws Exception {
        try(var f=new Fixture()) {
            CountDownLatch deferred=new CountDownLatch(1);
            var first=f.submit("first",command -> {
                f.queue.defer(f.key,100,() -> f.trace.add("retry-completed")); deferred.countDown();
            });
            await(deferred); f.clock.at(50); var later=f.submit("later",command -> {});
            var keyB=f.queue.register(20,f.handler());
            done(f.queue.submit(keyB,new Command("B",command -> {})));
            assertThat(later.processed()).isNotDone(); assertThat(later.ingress().receivedAtMs()).isEqualTo(50);
            f.clock.at(100); f.scheduler.tasks.getFirst().fire(); done(first); done(later);
            assertThat(f.trace).containsExactly("first","B","retry-completed","later");
            assertThat(later.ingress().sequence()).isEqualTo(2);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"question", "phase", "token"})
    void oldTimerWithWrongQuestionPhaseOrTokenAndRepeatedCloseAreNoOps(String changed) throws Exception {
        try (var f = new Fixture()) {
            f.open(1, Phase.QUESTION_OPEN, 1, 100);
            var old = f.scheduler.tasks.getFirst();
            PhaseWindow current = f.open(changed.equals("question") ? 2 : 1,
                    changed.equals("phase") ? Phase.DECISION : Phase.QUESTION_OPEN,
                    changed.equals("token") ? 2 : 1, 100);
            var gate = new QuestionCloseGate(Set.of(1L));
            AtomicInteger closes = new AtomicInteger();
            f.onTimer = timer -> {
                assertThat(current.matches(timer.value().questionIndex(), timer.value().phase(), timer.value().token())).isTrue();
                if (gate.close()) closes.incrementAndGet();
            };
            f.clock.at(100); old.fire(); f.barrier();
            assertThat(old.cancelled).isTrue(); assertThat(closes).hasValue(0);
            var live = f.scheduler.tasks.getLast(); live.fire(); live.fire(); f.barrier();
            live.fire(); f.barrier();
            assertThat(closes).hasValue(1); assertThat(f.trace).filteredOn("timer"::equals).hasSize(1);
        }
    }

    @Test void queuedOldTimerCannotCloseNewPhase() throws Exception {
        try (var f = new Fixture()) {
            f.open(1, Phase.DECISION, 1, 100);
            var old = f.scheduler.tasks.getFirst();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var blocked = f.submit("block", command -> { entered.countDown(); await(release); }); await(entered);
            try {
                var reopen = f.submit("open-new", command -> f.queue.armTimer(f.key, PhaseWindow.open(1, Phase.QUESTION_OPEN, 2, 100, 1_000_100, 100)));
                f.clock.at(100); old.fire(); release.countDown(); done(blocked); done(reopen); f.barrier();
                assertThat(f.trace).doesNotContain("timer");
                f.clock.at(200); f.scheduler.tasks.getLast().fire(); f.barrier();
                assertThat(f.trace).filteredOn("timer"::equals).hasSize(1);
            } finally { release.countDown(); }
        }
    }

    @Test void earlySchedulerWakeupReschedulesAndEpochJumpsCannotChangeDeadline() throws Exception {
        try (var f = new Fixture()) {
            var window = f.open(1, Phase.QUESTION_OPEN, 1, 100);
            f.clock.at(99, 9_000_000); f.scheduler.tasks.getFirst().fire(); f.barrier();
            assertThat(f.trace).doesNotContain("timer");
            assertThat(f.scheduler.tasks.getLast().delay).isEqualTo(1);
            assertThat(window.deadlineEpochMs()).isEqualTo(1_000_100); assertThat(window.remainingMs(99)).isEqualTo(1);
            f.clock.at(100, 50); f.scheduler.tasks.getLast().fire(); f.barrier();
            assertThat(f.trace).filteredOn("timer"::equals).hasSize(1);
        }
    }

    @Test void newDeadlineStartsWhenHandlerActuallyOpensNotFromCommandReceipt() throws Exception {
        try (var f = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var block = f.submit("block", command -> { entered.countDown(); await(release); }); await(entered);
            AtomicReference<PhaseWindow> opened = new AtomicReference<>();
            try {
                f.clock.at(10);
                var open = f.submit("open", command -> {
                    var now = f.clock.sample();
                    var window = PhaseWindow.open(1, Phase.DECISION, 1, now.monotonicMs(), now.epochMs(), 5_000);
                    opened.set(window); f.queue.armTimer(f.key, window);
                });
                f.clock.at(500); release.countDown(); done(block); done(open);
                assertThat(open.ingress().receivedAtMs()).isEqualTo(10);
                assertThat(opened.get().openedAtMs()).isEqualTo(500);
                assertThat(opened.get().deadlineMs()).isEqualTo(5_500);
                assertThat(opened.get().deadlineEpochMs()).isEqualTo(1_005_500);
                assertThat(f.scheduler.tasks.getFirst().delay).isEqualTo(5_000);
            } finally { release.countDown(); }
        }
    }

    @Test void earlyCloseIncludesDisconnectedPlayerAndAllowsOneCloseScoringCallback() throws Exception {
        try (var f = new Fixture()) {
            var window = f.open(1, Phase.QUESTION_OPEN, 1, 100);
            // Player 3 is disconnected, still PLAYING and therefore still eligible. No connection filter.
            var gate = new QuestionCloseGate(Set.of(1L, 2L, 3L));
            AtomicInteger scoreCalls = new AtomicInteger();
            f.onTimer = timer -> { if (gate.close()) scoreCalls.incrementAndGet(); };
            f.clock.at(99);
            for (long id : new long[] {1, 2}) f.run("answer", command -> {
                assertThat(window.accepts(command.receivedAtMs())).isTrue();
                gate.recordValidAnswer(id); if (gate.closeIfAllAnswered()) scoreCalls.incrementAndGet();
            });
            assertThat(scoreCalls).hasValue(0);
            f.run("invalid-and-duplicate", command -> {
                assertThat(gate.recordValidAnswer(1)).isFalse(); assertThat(gate.recordValidAnswer(99)).isFalse();
                assertThat(gate.closeIfAllAnswered()).isFalse();
            });
            f.run("answer-disconnected", command -> {
                assertThat(gate.recordValidAnswer(3)).isTrue(); if (gate.closeIfAllAnswered()) scoreCalls.incrementAndGet();
                assertThat(gate.recordValidAnswer(3)).isFalse(); assertThat(gate.close()).isFalse();
            });
            f.clock.at(100); f.scheduler.tasks.getFirst().fire(); f.scheduler.tasks.getFirst().fire(); f.barrier();
            assertThat(scoreCalls).hasValue(1);
        }
    }

    @Test void disconnectedUnansweredPlayerWaitsForTimerInsteadOfEarlyClose() throws Exception {
        try (var f = new Fixture()) {
            f.open(1, Phase.QUESTION_OPEN, 1, 100);
            var gate = new QuestionCloseGate(Set.of(1L, 2L, 3L));
            AtomicInteger closes = new AtomicInteger();
            f.onTimer = timer -> { if (gate.close()) closes.incrementAndGet(); };
            f.run("two-answers", command -> { gate.recordValidAnswer(1); gate.recordValidAnswer(2); assertThat(gate.closeIfAllAnswered()).isFalse(); });
            assertThat(gate.isClosed()).isFalse(); f.clock.at(100); f.scheduler.tasks.getFirst().fire(); f.barrier();
            assertThat(closes).hasValue(1);
        }
    }

    @Test void retentionKeepsRetryHandlerThenCleanupRejectsOldKeyAndTimerAfterIdReuse() throws Exception {
        try (var f = new Fixture()) {
            f.open(1, Phase.QUESTION_OPEN, 1, 100);
            var old = f.scheduler.tasks.getFirst(); f.run("finish", command -> f.queue.retire(f.key));
            f.clock.at(599_999); assertThat(f.queue.cleanup()).isZero(); f.run("retry", command -> {});
            assertThat(f.trace).contains("retry"); f.clock.at(600_000);
            assertThatThrownBy(() -> f.submit("too-late", command -> {})).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            // Barrier completion is before drain becomes idle; register cleanup must not evict an active processor.
            awaitIdleCleanup(f.queue);
            var fresh = f.queue.register(10, f.handler());
            assertThat(fresh.generation()).isNotEqualTo(f.key.generation());
            assertThatThrownBy(() -> f.submit("old-key", command -> {})).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            done(f.queue.submit(fresh, new Command("fresh-open", command -> {
                f.queue.armTimer(fresh, PhaseWindow.open(2, Phase.QUESTION_OPEN, 1, 600_000, 1_600_000, 100));
            })));
            old.fire(); done(f.queue.submit(fresh, new Command("fresh-barrier", command -> {})));
            assertThat(f.trace).doesNotContain("timer");
            f.clock.at(600_100); f.scheduler.tasks.getLast().fire(); done(f.queue.submit(fresh, new Command("fresh-barrier", command -> {})));
            assertThat(f.ingress.getLast().sessionGeneration()).isEqualTo(fresh.generation());
            assertThat(f.trace).filteredOn("timer"::equals).hasSize(1);
        }
    }

    private static void awaitIdleCleanup(SessionQueue<?> queue) {
        // Condition-based, bounded yielding; no sleep or time assumption proves the race.
        long bound = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (queue.cleanup() == 0) {
            if (System.nanoTime() >= bound) throw new AssertionError("Processor did not become idle");
            Thread.yield();
        }
    }

    @Test void cleanupCannotEvictBlockedRetryAndTimerStillCannotEnterTerminalSession() throws Exception {
        try (var f = new Fixture()) {
            f.open(1, Phase.QUESTION_OPEN, 1, 100); var old = f.scheduler.tasks.getFirst();
            f.run("finish", command -> f.queue.retire(f.key));
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var retry = f.submit("retry", command -> { entered.countDown(); await(release); }); await(entered);
            try {
                f.clock.at(600_000); assertThat(f.queue.cleanup()).isZero(); old.fire();
                assertThat(f.trace).doesNotContain("timer");
                assertThatThrownBy(() -> f.queue.register(10, f.handler())).isInstanceOf(IllegalStateException.class);
            } finally { release.countDown(); }
            done(retry); awaitIdleCleanup(f.queue);
        }
    }

    @Test void capacityRejectsNewCommandsBeforeIngressButReservedTimerStillCloses() throws Exception {
        try (var f = new Fixture(1, 1, 1)) {
            f.open(1, Phase.QUESTION_OPEN, 1, 100);
            CountDownLatch timerDelivered = new CountDownLatch(1);
            f.onTimer = timer -> timerDelivered.countDown();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var block = f.submit("block", command -> { entered.countDown(); await(release); }); await(entered);
            try {
                var pending = f.submit("pending", command -> {});
                assertThatThrownBy(() -> f.submit("overflow", command -> {})).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                assertThatThrownBy(() -> f.queue.register(20, f.handler())).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                f.clock.at(100); f.scheduler.tasks.getFirst().fire(); release.countDown(); done(block); done(pending);
                await(timerDelivered);
                assertThat(f.trace).containsSubsequence("open", "block", "pending", "timer").doesNotContain("overflow");
                assertThat(f.ingress).extracting(Ingress::sequence).startsWith(1L, 2L, 3L, 4L);
            } finally { release.countDown(); }
        }
    }

    @Test void unexpectedHandlerFailureFencesQueueWithoutRepeatingSideEffect() throws Exception {
        try (var f = new Fixture()) {
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var failing = f.submit("fail", command -> { calls.incrementAndGet(); entered.countDown(); await(release); throw new IllegalStateException("callback failed"); });
            await(entered);
            try {
                var pending = f.submit("pending", command -> calls.incrementAndGet()); release.countDown();
                assertThatThrownBy(() -> done(failing)).hasCauseInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> done(pending)).hasCauseInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> f.submit("retry", command -> {})).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                assertThat(calls).hasValue(1);
            } finally { release.countDown(); }
        }
    }

    @Test void shutdownCancelsTimerAndRejectsPendingAndFutureCommands() throws Exception {
        var f = new Fixture();
        try {
            f.open(1, Phase.QUESTION_OPEN, 1, 100);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var block = f.submit("block", command -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test barrier timed out"); }
                catch (InterruptedException expectedShutdown) { Thread.currentThread().interrupt(); }
            });
            await(entered);
            var pending = f.submit("pending", command -> {});
            try {
                f.close(); assertThatThrownBy(() -> done(pending)).hasCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                f.scheduler.tasks.getFirst().fire(); assertThat(f.trace).doesNotContain("timer");
                assertThat(f.scheduler.closed).isTrue(); assertThat(f.scheduler.tasks.getFirst().cancelled).isTrue();
                assertThatThrownBy(() -> f.submit("new", command -> {})).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            } finally { release.countDown(); }
            done(block); // This test handler handles shutdown interruption; no claim of transaction cancellation.
        } finally { f.close(); }
    }
}
