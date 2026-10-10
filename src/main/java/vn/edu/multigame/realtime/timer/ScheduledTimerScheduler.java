package vn.edu.multigame.realtime.timer;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One shared scheduler thread; cancelled tasks are removed immediately. */
public final class ScheduledTimerScheduler implements TimerScheduler {
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "game-timer");
        thread.setDaemon(true);
        return thread;
    });

    public ScheduledTimerScheduler() {
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override public Ticket schedule(Runnable callback, long delayMs) {
        var future = executor.schedule(callback, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override public void close() { executor.shutdownNow(); }
}
