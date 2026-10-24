package vn.edu.multigame.realtime.timer;
import org.springframework.context.annotation.*;
@Configuration @Profile("mysql")
public class GameTimerConfiguration {
    @Bean public ServerClock gameClock() { return ServerClock.system(); }
}
