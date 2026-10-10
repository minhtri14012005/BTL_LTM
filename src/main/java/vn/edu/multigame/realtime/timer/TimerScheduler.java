package vn.edu.multigame.realtime.timer;

/** Small scheduling port; callbacks enqueue events, never mutate game state. */
public interface TimerScheduler extends AutoCloseable {
    interface Ticket { void cancel(); }
    Ticket schedule(Runnable callback, long delayMs);
    @Override void close();
}
