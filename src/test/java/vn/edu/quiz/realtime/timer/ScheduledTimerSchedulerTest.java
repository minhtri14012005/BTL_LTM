package vn.edu.quiz.realtime.timer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScheduledTimerSchedulerTest {
    @Test void realSchedulerDeliversDueCallbackAndCanCancelAndShutdown() throws Exception {
        var scheduler = new ScheduledTimerScheduler();
        try {
            CountDownLatch delivered = new CountDownLatch(1);
            scheduler.schedule(delivered::countDown, 0);
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
            // Long delay is not waited out: cancellation/shutdown removes pending timer resources.
            var pending = scheduler.schedule(() -> { throw new AssertionError("Cancelled timer ran"); }, 600_000);
            pending.cancel(); pending.cancel(); scheduler.close();
            assertThatThrownBy(() -> scheduler.schedule(() -> {}, 0)).isInstanceOf(RejectedExecutionException.class);
        } finally { scheduler.close(); }
    }
}
