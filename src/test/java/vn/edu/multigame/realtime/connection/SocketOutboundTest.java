package vn.edu.multigame.realtime.connection;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.web.socket.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocketOutboundTest {
    final ExecutorService writers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("test-socket-",0).factory());
    final ScheduledExecutorService watchdog=mock(ScheduledExecutorService.class);
    final WebSocketSession socket=mock(WebSocketSession.class);
    final List<Runnable> deadlines=new CopyOnWriteArrayList<>();
    final List<CloseStatus> detached=new CopyOnWriteArrayList<>();
    final List<String> sent=new CopyOnWriteArrayList<>();
    final CountDownLatch release=new CountDownLatch(1),entered=new CountDownLatch(1);
    final SocketOutbound outbound=new SocketOutbound(socket,writers,watchdog,detached::add);
    @BeforeEach void setup() throws Exception {
        when(socket.isOpen()).thenReturn(true);
        when(watchdog.schedule(any(Runnable.class),eq(5000L),eq(TimeUnit.MILLISECONDS))).thenAnswer(call->{
            deadlines.add(call.getArgument(0));return mock(ScheduledFuture.class);
        });
        doAnswer(call->{sent.add(((TextMessage)call.getArgument(0)).getPayload());return null;}).when(socket).sendMessage(any());
    }
    @AfterEach void cleanup() {release.countDown();outbound.stop();writers.shutdownNow();}
    void blockOn(String payload) throws Exception {
        doAnswer(call->{
            String text=((TextMessage)call.getArgument(0)).getPayload();sent.add(text);
            if(text.equals(payload)) {
                entered.countDown();boolean interrupted=false;
                while(true)try {release.await();break;}catch(InterruptedException e){interrupted=true;}
                if(interrupted)Thread.currentThread().interrupt();
            }
            return null;
        }).when(socket).sendMessage(any());
    }
    void await(CountDownLatch latch)throws Exception {assertThat(latch.await(3,TimeUnit.SECONDS)).isTrue();}

    @Test void blockedWriterPreservesAckAndEventOrderWithoutConcurrentSends() throws Exception {
        blockOn("ACK");outbound.send(new TextMessage("ACK"));await(entered);
        outbound.send(new TextMessage("QUESTION_CLOSED"));outbound.send(new TextMessage("SCORING_STARTED"));outbound.send(new TextMessage("QUESTION_RESULT"));
        assertThat(sent).containsExactly("ACK");release.countDown();
        verify(socket,timeout(3000)).sendMessage(new TextMessage("QUESTION_RESULT"));
        assertThat(sent).containsExactly("ACK","QUESTION_CLOSED","SCORING_STARTED","QUESTION_RESULT");assertThat(detached).isEmpty();
    }
    @Test void replacementDropsUnsentOldFramesThenNotifiesAndClosesInOrder() throws Exception {
        blockOn("in-flight");outbound.send(new TextMessage("in-flight"));await(entered);
        outbound.send(new TextMessage("old-pending"));outbound.finish(new TextMessage("SESSION_REPLACED"),new CloseStatus(4002,"SESSION_REPLACED"));
        outbound.send(new TextMessage("stale-new"));release.countDown();
        verify(socket,timeout(3000)).close(new CloseStatus(4002,"SESSION_REPLACED"));
        var order=inOrder(socket);order.verify(socket).sendMessage(new TextMessage("in-flight"));order.verify(socket).sendMessage(new TextMessage("SESSION_REPLACED"));order.verify(socket).close(new CloseStatus(4002,"SESSION_REPLACED"));
        assertThat(sent).containsExactly("in-flight","SESSION_REPLACED");
    }
    @Test void staleWatchdogCannotCloseLaterFrameAndLiveTimeoutDetachesOnlyOnce() throws Exception {
        blockOn("second");outbound.send(new TextMessage("first"));outbound.send(new TextMessage("second"));await(entered);
        outbound.send(new TextMessage("never-send"));deadlines.getFirst().run();assertThat(detached).isEmpty();
        deadlines.get(1).run();deadlines.get(1).run();
        assertThat(detached).containsExactly(new CloseStatus(1011,"SEND_TIMEOUT"));
        verify(socket,timeout(3000)).close(new CloseStatus(1011,"SEND_TIMEOUT"));release.countDown();
        assertThat(sent).containsExactly("first","second");
    }
    @Test void bufferIncludesInflightBytesAndRejectsOverflowWithoutRetry() throws Exception {
        String first="a".repeat(200000);blockOn(first);outbound.send(new TextMessage(first));await(entered);
        outbound.send(new TextMessage("b".repeat(100000)));outbound.send(new TextMessage("never-send"));
        assertThat(detached).containsExactly(new CloseStatus(1011,"SEND_BUFFER_LIMIT"));
        verify(socket,timeout(3000)).close(new CloseStatus(1011,"SEND_BUFFER_LIMIT"));assertThat(sent).containsExactly(first);
    }
    @Test void messageCountBoundsSmallFramesAndOverflowDetaches() throws Exception {
        blockOn("first");outbound.send(new TextMessage("first"));await(entered);
        for(int i=1;i<SocketOutbound.MESSAGE_LIMIT;i++)outbound.send(new TextMessage(""));
        assertThat(detached).isEmpty();outbound.send(new TextMessage(""));
        assertThat(detached).containsExactly(new CloseStatus(1011,"SEND_BUFFER_LIMIT"));assertThat(sent).containsExactly("first");
    }
    @Test void ioFailureDetachesBeforeBlockingCloseAndDoesNotRepeatSend() throws Exception {
        var closing=new CountDownLatch(1);
        doThrow(new IOException("injected")).when(socket).sendMessage(any());
        doAnswer(call->{closing.countDown();release.await();return null;}).when(socket).close(any());
        outbound.send(new TextMessage("ACK"));await(closing);outbound.send(new TextMessage("later"));
        assertThat(detached).containsExactly(new CloseStatus(1011,"SEND_UNAVAILABLE"));verify(socket,times(1)).sendMessage(any());
    }
}
