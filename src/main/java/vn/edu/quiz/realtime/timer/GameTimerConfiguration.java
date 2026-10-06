package vn.edu.quiz.realtime.timer;
import org.springframework.context.annotation.*;
@Configuration @Profile("mysql")
public class GameTimerConfiguration {
    @Bean public ServerClock gameClock() { return ServerClock.system(); }
    @Bean(destroyMethod="") public TimerScheduler gameScheduler() { return new ScheduledTimerScheduler(); }
}
