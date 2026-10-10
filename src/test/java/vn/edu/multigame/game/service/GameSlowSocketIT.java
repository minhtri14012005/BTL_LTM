package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.*;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real GameRuntime, synchronous Spring events, MySQL and a real healthy WS peer.
 * Only the slow transport is injected; a latch holds sendMessage until explicitly released. */
class GameSlowSocketIT extends GameNetworkFixture {
    @Autowired AuthenticatedSocketRegistry sockets;

    @ParameterizedTest @ValueSource(ints={1,4})
    void blockedSendCannotHoldCommandsTimersOrOtherClients(int slowCount) throws Exception {
        var release=new CountDownLatch(1);var entered=new CountDownLatch(slowCount);
        var sendThread=new AtomicReference<String>();var slowSockets=new ArrayList<WebSocketSession>();
        var sessionIds=new ArrayList<String>();var fixtures=new ArrayList<Fixture>();var ids=new ArrayList<Long>();
        try {
            for(int i=0;i<slowCount;i++) {
                var f=fixture();fixtures.add(f);var login=new Client(f.host());
                String session=login.cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
                var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("slow-"+i);
                when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());when(socket.getPrincipal()).thenReturn(f.host().auth());when(socket.isOpen()).thenReturn(true);
                doAnswer(call->{
                    if(((TextMessage)call.getArgument(0)).getPayload().contains("QUESTION_START")) {
                        sendThread.set(Thread.currentThread().getName());entered.countDown();
                        if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Slow socket barrier timed out");
                    }
                    return null;
                }).when(socket).sendMessage(any());
                sockets.add(session,socket,f.host().id());sockets.connected(socket);sockets.subscribe(socket,f.room().id());
                slowSockets.add(socket);sessionIds.add(session);ids.add(start(f));
            }
            var peer=new Wire(fixtures.getFirst().roster().getFirst());peer.subscribe(fixtures.getFirst());
            // Create the unblocked Game before saturating all four gameplay workers.
            var other=fixture();long otherId=start(other);
            long openAt=decision(ids.getFirst(),1).deadlineEpochMs()-clock.epoch;
            scheduler.advance(openAt);assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            long measuredAt=System.nanoTime();
            var command=runtime.reconnect(ids.getFirst(),fixtures.getFirst().host().id(),()->{},s->{});
            var otherCommand=runtime.reconnect(otherId,other.host().id(),()->{},s->{});
            // Opening arms the question deadline before publishing QUESTION_START.
            scheduler.advance(openAt+1000);
            boolean sameDone=completedWithin(command,300),otherDone=completedWithin(otherCommand,300);
            boolean resultDelivered=awaitMessage(peer,"QUESTION_RESULT",300);
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-measuredAt);
            System.out.printf("SLOW_SOCKET count=%d sender=%s measuredMs=%d sameCommand=%s otherCommand=%s healthyResult=%s timerResultRows=%d%n",
                    slowCount,sendThread.get(),elapsed,sameDone,otherDone,resultDelivered,answerCount(ids.getFirst()));
            assertThat(sameDone).as("same Game command while send is blocked").isTrue();
            assertThat(otherDone).as("other Game with %s blocked sockets",slowCount).isTrue();
            assertThat(resultDelivered).as("timer/Close/Score/Result reaches healthy peer before release").isTrue();
            assertThat(answerCount(ids.getFirst())).isEqualTo(3);
            assertThat(peer.messages.stream().filter(m->m.path("kind").asText().equals("EVENT") && m.path("target").path("kind").asText().equals("GAME"))
                    .map(m->m.path("type").asText()).toList()).containsSubsequence("QUESTION_START","QUESTION_CLOSED","SCORING_STARTED","QUESTION_RESULT");
            assertThat(sendThread.get()).doesNotStartWith("game-worker-");
            release.countDown();
            for(var socket:slowSockets)verify(socket,timeout(3000)).sendMessage(argThat(m->m.getPayload().toString().contains("QUESTION_RESULT")));
        } finally {
            release.countDown();
            for(int i=0;i<slowSockets.size();i++)sockets.remove(sessionIds.get(i),slowSockets.get(i));
        }
    }
    boolean completedWithin(CompletableFuture<?> future,long ms)throws Exception {
        try {future.get(ms,TimeUnit.MILLISECONDS);return true;}catch(TimeoutException e){return false;}
    }
    boolean awaitMessage(Wire peer,String type,long ms) {
        long bound=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);
        do {if(peer.messages.stream().anyMatch(m->m.path("type").asText().equals(type)))return true;Thread.yield();}while(System.nanoTime()<bound);
        return false;
    }
}
