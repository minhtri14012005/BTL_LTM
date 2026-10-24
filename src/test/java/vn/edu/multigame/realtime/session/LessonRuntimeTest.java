package vn.edu.multigame.realtime.session;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import vn.edu.multigame.game.enums.Phase;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.lesson.repository.LessonRealtimeStore;
import vn.edu.multigame.lesson.service.LessonRealtimeTransactions;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.multigame.realtime.message.command.LessonCommand;
import vn.edu.multigame.realtime.timer.ServerClock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LessonRuntimeTest {
    @Test void visitingMoreThanTheActorCapacityReclaimsIdleLessonsInsteadOfRuntimeBusy() {
        var store=mock(LessonRealtimeStore.class);var transactions=mock(LessonRealtimeTransactions.class);var sockets=mock(AuthenticatedSocketRegistry.class);
        when(sockets.lessonSubscriptions()).thenReturn(Map.of());
        try(var runtime=new LessonRuntime(ServerClock.system(),store,transactions,sockets)) {
            var caller=mock(Authentication.class);
            for(long lesson=1;lesson<=600;lesson++)assertThat(runtime.serial(caller,lesson,()->true)).isTrue();
        }
    }
    @Test void liveWindowKeepsItsOriginalMonotonicDeadlineWhenWallClockJumps() throws Exception {
        var now=new AtomicReference<>(new ServerClock.Sample(100,10000));ServerClock clock=now::get;
        var store=mock(LessonRealtimeStore.class);var transactions=mock(LessonRealtimeTransactions.class);var sockets=mock(AuthenticatedSocketRegistry.class);
        var persisted=new AtomicReference<LessonRealtimeStore.Window>();
        when(store.window(1)).thenAnswer(ignored->Optional.ofNullable(persisted.get()));
        when(sockets.lessonSubscriptions()).thenReturn(Map.of());
        var view=new LessonRealtimeView(new LessonView(1,1,1,"Lesson",true,"RUNNING",1,List.of(),false,BigDecimal.ZERO),null,10000);
        when(transactions.snapshot(any(),eq(1L))).thenReturn(view);
        var phase=PhaseWindow.open(1,Phase.QUESTION_OPEN,1,100,10000,1000);
        var calls=new AtomicInteger();var observed=new AtomicReference<PhaseWindow>();
        when(transactions.execute(any(),any(),any(),nullable(PhaseWindow.class),any())).thenAnswer(invocation->{
            Ingress<?> ingress=invocation.getArgument(2);
            if(calls.incrementAndGet()==1) {
                now.set(new ServerClock.Sample(101,999000));
                persisted.set(new LessonRealtimeStore.Window(1,1,1,1,1,"OPEN",10000,11000));
            } else observed.set(invocation.getArgument(3));
            return new LessonRealtimeTransactions.Result(view,ingress.sequence(),ingress.serverTimeMs(),false,phase);
        });
        var auth=mock(Authentication.class);var json=new ObjectMapper();
        var open=new LessonCommand(1,"COMMAND",UUID.randomUUID().toString(),"OPEN_LESSON_WINDOW",LessonCommand.Target.lesson(1),json.createObjectNode());
        var reconnect=new LessonCommand(1,"COMMAND",UUID.randomUUID().toString(),"RECONNECT_LESSON",LessonCommand.Target.lesson(1),json.createObjectNode());
        try(var runtime=new LessonRuntime(clock,store,transactions,sockets)) {
            runtime.command(auth,open,()->{}).get(5,TimeUnit.SECONDS);
            runtime.command(auth,reconnect,()->{}).get(5,TimeUnit.SECONDS);
            assertThat(observed.get()).isEqualTo(phase);
            assertThat(observed.get().accepts(1099)).isTrue();assertThat(observed.get().accepts(1100)).isFalse();
        }
    }
}
