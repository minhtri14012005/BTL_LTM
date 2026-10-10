package vn.edu.multigame.realtime.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.realtime.timer.PhaseTimer;
import vn.edu.multigame.realtime.timer.ScheduledTimerScheduler;
import vn.edu.multigame.realtime.timer.ServerClock;
import vn.edu.multigame.realtime.timer.TimerScheduler;

/** Bounded per-game serial processors on a shared pool. Not a receipt cache or a transaction manager. */
public final class SessionQueue<C> implements AutoCloseable {
    public static final long RETENTION_MS = 600_000;
    public record SessionKey(long gameSessionId, long generation) {}
    public record Submission<T>(Ingress<T> ingress, CompletableFuture<Void> processed) {}
    public interface Handler<T> {
        void onCommand(Ingress<T> command);
        void onTimer(Ingress<PhaseTimer> timer);
    }
    private record Work(Runnable invoke, PhaseTimer timer, CompletableFuture<Void> completion, boolean command) {}
    private final class State {
        final SessionKey key;
        final Handler<C> handler;
        final ArrayDeque<Work> queue = new ArrayDeque<>();
        long sequence;
        int pendingCommands;
        boolean running, retired, disposed, failed, timerEnqueued, paused;
        long retryToken;
        TimerScheduler.Ticket retryTicket;
        long retiredAtMs;
        Thread owner;
        PhaseTimer timer;
        TimerScheduler.Ticket ticket;
        State(SessionKey key, Handler<C> handler) { this.key = key; this.handler = handler; }
    }

    private final Map<Long, State> states = new HashMap<>();
    private final ServerClock clock;
    private final TimerScheduler scheduler;
    private final ThreadPoolExecutor workers;
    private final int maxSessions, commandLimit, batchSize;
    private long generation;
    private volatile boolean closed;

    public SessionQueue(ServerClock clock) {
        this(clock, new ScheduledTimerScheduler(), 4, 512, 64, 32);
    }

    /** Owns scheduler and workers; the caller closes this runtime during server shutdown. No Spring wiring yet. */
    public SessionQueue(ServerClock clock, TimerScheduler scheduler, int workerCount,
            int maxSessions, int commandLimit, int batchSize) {
        if (workerCount < 2 || workerCount > 32 || maxSessions < 1 || commandLimit < 1 || batchSize < 1) {
            throw new IllegalArgumentException("Invalid pool/queue limits");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.maxSessions = maxSessions; this.commandLimit = commandLimit; this.batchSize = batchSize;
        AtomicInteger names = new AtomicInteger();
        workers = new ThreadPoolExecutor(workerCount, workerCount, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxSessions), runnable -> {
                    Thread thread = new Thread(runnable, "game-worker-" + names.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Register only after Start commit in Task 8. Explicit keys prevent implicit recreation by stale commands. */
    public SessionKey register(long gameSessionId, Handler<C> handler) {
        if (gameSessionId <= 0) throw new IllegalArgumentException("Invalid GameSession ID");
        Objects.requireNonNull(handler, "handler");
        cleanup();
        synchronized (states) {
            if (closed) throw unavailable();
            if (states.containsKey(gameSessionId)) throw new IllegalStateException("Session already registered");
            if (states.size() >= maxSessions) throw unavailable();
            SessionKey key = new SessionKey(gameSessionId, Math.incrementExact(generation));
            generation = key.generation();
            states.put(gameSessionId, new State(key, handler));
            return key;
        }
    }

    /** Successful enqueue is ingress. Waiting for this monitor is not ingress, and client time is never read. */
    public Submission<C> submit(SessionKey key, C command) {
        return admit(key,command,true);
    }

    /** Trusted presence/close continuations share ingress order; command floods cannot discard them. */
    Submission<C> submitControl(SessionKey key,C command) {
        return admit(key,command,false);
    }

    private Submission<C> admit(SessionKey key,C command,boolean counted) {
        Objects.requireNonNull(command, "command");
        State state = state(key);
        synchronized (state) {
            requireAvailable(state);
            if (counted && state.pendingCommands >= commandLimit) throw unavailable();
            ServerClock.Sample now = clock.sample();
            long sequence = Math.incrementExact(state.sequence);
            Ingress<C> ingress = new Ingress<>(key.gameSessionId(), key.generation(), sequence,
                    now.monotonicMs(), now.epochMs(), command);
            var completion = new CompletableFuture<Void>();
            Work work = new Work(() -> state.handler.onCommand(ingress), null, completion, counted);
            state.sequence = sequence;
            state.queue.addLast(work); if(counted) state.pendingCommands++;
            try { dispatch(state); }
            catch (RejectedExecutionException failure) {
                state.queue.removeLast(); if(counted) state.pendingCommands--; state.sequence--;
                throw failure; // Not admitted, no side effect and no returned ingress receipt.
            }
            return new Submission<>(ingress, completion);
        }
    }

    /** Call from this session's handler after opening a phase using a fresh clock sample. */
    public PhaseTimer armTimer(SessionKey key, PhaseWindow window) {
        Objects.requireNonNull(window, "window");
        State state = state(key);
        synchronized (state) {
            requireOwner(state);
            if (state.retired) throw new IllegalStateException("Terminal session cannot arm timers");
            cancelTimer(state);
            // Stale queued timers have no command completion/replay data and can be removed safely.
            state.queue.removeIf(work -> work.timer() != null);
            PhaseTimer timer = new PhaseTimer(key.gameSessionId(), key.generation(), window.questionIndex(),
                    window.phase(), window.token(), window.deadlineMs());
            state.timer = timer;
            state.ticket = scheduler.schedule(() -> timerIngress(state, timer), window.remainingMs(clock.sample().monotonicMs()));
            return timer;
        }
    }

    /** Terminal handlers remain callable for authorized retry/replay during ACTIVE + FINISHED retention. */
    public void retire(SessionKey key) {
        State state = state(key);
        synchronized (state) {
            requireOwner(state);
            if (!state.retired) { state.retired = true; state.retiredAtMs = clock.sample().monotonicMs(); }
            cancelTimer(state);
            state.queue.removeIf(work -> work.timer() != null);
        }
    }
    public void stopTimer(SessionKey key) {
        State state=state(key);
        synchronized(state) { requireOwner(state); cancelTimer(state); state.queue.removeIf(work -> work.timer()!=null); }
    }

    /** Continue the same operation after backoff, holding later ingress without occupying a worker. */
    public void defer(SessionKey key, long delayMs, Runnable continuation) {
        if (delayMs < 0) throw new IllegalArgumentException("Negative backoff");
        Objects.requireNonNull(continuation, "continuation");
        State state = state(key);
        synchronized (state) {
            requireOwner(state);
            if (state.paused) throw new IllegalStateException("Operation already deferred");
            state.paused = true;
            long token = ++state.retryToken;
            state.retryTicket = scheduler.schedule(() -> {
                synchronized (state) {
                    if (closed || state.disposed || state.failed || !state.paused || state.retryToken != token) return;
                    state.paused = false; state.retryTicket = null;
                    state.queue.addFirst(new Work(continuation, null, null, false));
                    dispatch(state);
                }
            }, delayMs);
        }
    }

    /** Invoke periodically from the future lifecycle owner; registration also reclaims expired idle sessions. */
    public int cleanup() {
        int removed = 0;
        synchronized (states) {
            var iterator = states.values().iterator();
            while (iterator.hasNext()) {
                State state = iterator.next();
                synchronized (state) {
                    if (state.retired && !state.running && !state.paused && state.queue.isEmpty()
                            && clock.sample().monotonicMs() - state.retiredAtMs >= RETENTION_MS) {
                        state.disposed = true; cancelTimer(state); iterator.remove(); removed++;
                    }
                }
            }
        }
        return removed;
    }

    private void timerIngress(State state, PhaseTimer timer) {
        synchronized (state) {
            if (closed || state.disposed || state.failed || state.retired || state.timer != timer || state.timerEnqueued) return;
            ServerClock.Sample now = clock.sample();
            if (now.monotonicMs() < timer.deadlineMs()) {
                state.ticket = scheduler.schedule(() -> timerIngress(state, timer), timer.deadlineMs() - now.monotonicMs());
                return; // Early scheduler wakeup: never close ahead of the monotonic deadline.
            }
            long sequence = Math.incrementExact(state.sequence);
            var ingress = new Ingress<>(state.key.gameSessionId(), state.key.generation(), sequence,
                    now.monotonicMs(), now.epochMs(), timer);
            state.sequence = sequence; state.timerEnqueued = true;
            // One reserved timer slot per session: a command flood cannot drop the close event.
            state.queue.addLast(new Work(() -> state.handler.onTimer(ingress), timer, null, false));
            dispatch(state);
        }
    }

    private void dispatch(State state) {
        if (!state.running && !state.paused) {
            state.running = true;
            try { workers.execute(() -> drain(state)); }
            catch (RejectedExecutionException failure) { state.running = false; throw failure; }
        }
    }

    private void drain(State state) {
        for (int count = 0; count < batchSize; count++) {
            Work work;
            synchronized (state) {
                if (state.disposed || state.failed || state.paused) { state.running = false; return; }
                work = state.queue.pollFirst();
                if (work == null) { state.running = false; return; }
                if (work.timer() != null) {
                    if (state.timer != work.timer() || state.retired) continue;
                    cancelTimer(state); // Consume once before callback; duplicate timer deliveries become no-ops.
                } else if (work.command()) state.pendingCommands--;
                state.owner = Thread.currentThread();
            }
            Throwable failure = null;
            try { work.invoke().run(); }
            catch (RuntimeException | Error thrown) { failure = thrown; }
            finally { synchronized (state) { state.owner = null; } }
            if (failure != null) {
                List<Work> abandoned;
                synchronized (state) {
                    state.failed = true; state.running = false; cancelTimer(state);
                    abandoned = new ArrayList<>(state.queue); state.queue.clear(); state.pendingCommands = 0;
                }
                if (work.completion() != null) work.completion().completeExceptionally(failure);
                for (Work queued : abandoned) if (queued.completion() != null) queued.completion().completeExceptionally(failure);
                if (failure instanceof Error error) throw error;
                return; // No blind retry of a possibly partially executed business callback.
            }
            if (work.completion() != null) work.completion().complete(null);
        }
        synchronized (state) {
            state.running = false;
            if (!state.disposed && !state.queue.isEmpty()) dispatch(state); // Yield so other sessions can progress.
        }
    }

    private State state(SessionKey key) {
        Objects.requireNonNull(key, "key");
        synchronized (states) {
            State state = states.get(key.gameSessionId());
            if (state == null || !state.key.equals(key)) throw unavailable();
            return state;
        }
    }
    public boolean registered(SessionKey key) {
        synchronized (states) { State state=states.get(key.gameSessionId()); return state!=null && state.key.equals(key); }
    }
    private void requireAvailable(State state) {
        if (closed || state.disposed || state.failed) throw unavailable();
        if (state.retired && clock.sample().monotonicMs() - state.retiredAtMs >= RETENTION_MS) throw unavailable();
    }
    private void requireOwner(State state) {
        if (closed || state.disposed || state.failed || state.owner != Thread.currentThread()) {
            throw new IllegalStateException("Operation requires this session's serial handler");
        }
    }
    private void cancelTimer(State state) {
        if (state.ticket != null) state.ticket.cancel();
        state.ticket = null; state.timer = null; state.timerEnqueued = false;
    }
    private RejectedExecutionException unavailable() { return new RejectedExecutionException("Session unavailable or queue capacity reached"); }

    @Override public void close() {
        List<Work> abandoned = new ArrayList<>();
        synchronized (states) {
            closed = true;
            for (State state : states.values()) synchronized (state) {
                state.disposed = true; cancelTimer(state);
                if (state.retryTicket != null) state.retryTicket.cancel();
                abandoned.addAll(state.queue); state.queue.clear(); state.pendingCommands = 0;
            }
            states.clear();
        }
        scheduler.close(); workers.shutdownNow();
        for (Work work : abandoned) if (work.completion() != null) work.completion().completeExceptionally(unavailable());
    }
}
