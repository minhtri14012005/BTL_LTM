package vn.edu.quiz.realtime.timer;

import java.util.concurrent.TimeUnit;

/** Server time only. Monotonic milliseconds decide deadlines; epoch milliseconds are for display. */
public interface ServerClock {
    record Sample(long monotonicMs, long epochMs) {}
    Sample sample();

    static ServerClock system() {
        long origin = System.nanoTime();
        return () -> new Sample(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - origin), System.currentTimeMillis());
    }
}
