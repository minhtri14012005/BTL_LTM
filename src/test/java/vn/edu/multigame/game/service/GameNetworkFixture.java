package vn.edu.multigame.game.service;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.realtime.message.command.GameCommand;
import vn.edu.multigame.realtime.session.SessionQueue;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shared real raw WS fixture for gameplay and reconnect tests. */
abstract class GameNetworkFixture extends GameLifecycleFixture {
    @MockitoSpyBean SpinSelector spins;
    final List<Wire> wires=new ArrayList<>();
    @BeforeEach void resetDraw() { reset(spins); }
    @AfterEach void closeWires() { wires.forEach(Wire::close); reset(spins); }
    class Wire implements AutoCloseable {
        final Client client;
        final List<JsonNode> messages=new CopyOnWriteArrayList<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();
        final WebSocket socket;
        Wire(Account user) throws Exception {
            client=new Client(user); wires.add(this);
            socket=client.http.newWebSocketBuilder().header("Origin","http://localhost:8080")
                    .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),new WebSocket.Listener() {
                        final StringBuilder buffer=new StringBuilder();
                        public void onOpen(WebSocket socket) { socket.request(1); }
                        public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last) {
                            buffer.append(text); if(last) { try { messages.add(json.readTree(buffer.toString())); } catch(Exception e) { throw new AssertionError(e); } buffer.setLength(0); }
                            socket.request(1); return null;
                        }
                        public CompletionStage<?> onClose(WebSocket socket,int code,String reason) { closed.complete(code); return null; }
                    }).get(5,TimeUnit.SECONDS);
            next(m -> m.path("type").asText().equals("AUTH_READY"));
        }
        synchronized void send(JsonNode command) throws Exception { socket.sendText(command.toString(),true).get(5,TimeUnit.SECONDS); }
        JsonNode next(Predicate<JsonNode> predicate) { waitUntil(() -> messages.stream().anyMatch(predicate)); return messages.stream().filter(predicate).findFirst().orElseThrow(); }
        JsonNode response(JsonNode command) throws Exception {
            String requestId=command.path("requestId").asText();
            int previous=(int)messages.stream().filter(m -> m.path("requestId").asText().equals(requestId) && Set.of("ACK","ERROR").contains(m.path("kind").asText())).count();
            send(command); return response(requestId,previous+1);
        }
        JsonNode response(String requestId,int count) {
            Predicate<JsonNode> matches=m -> m.path("requestId").asText().equals(requestId) && Set.of("ACK","ERROR").contains(m.path("kind").asText());
            waitUntil(() -> messages.stream().filter(matches).count()>=count); return messages.stream().filter(matches).toList().get(count-1);
        }
        JsonNode event(String type,int index) { return next(m -> m.path("type").asText().equals(type) && m.path("payload").path("questionIndex").asInt()==index); }
        void subscribe(Fixture f) throws Exception {
            var command=envelope("SUBSCRIBE_ROOM","ROOM",f.room().id(),null,Map.of());
            assertThat(response(command).path("kind").asText()).isEqualTo("ACK");
        }
        public void close() { try { if(!socket.isOutputClosed()) socket.sendClose(WebSocket.NORMAL_CLOSURE,"review test complete").get(5,TimeUnit.SECONDS); } catch(Exception ignored) {} }
    }
    ObjectNode envelope(String type,String kind,long target,Integer index,Object payload) {
        var command=json.createObjectNode(); command.put("v",1).put("kind","COMMAND").put("requestId",UUID.randomUUID().toString()).put("type",type);
        command.set("target",json.valueToTree(Map.of("kind",kind,"id",target))); command.set("questionIndex",json.valueToTree(index)); command.set("payload",json.valueToTree(payload)); return command;
    }
    ObjectNode command(String type,long game,int index,Object payload) { return envelope(type,"GAME",game,index,payload); }
    void accepted(JsonNode response) { assertThat(response.path("kind").asText()).as(response.toString()).isEqualTo("ACK"); assertThat(response.path("status").asText()).isEqualTo("ACCEPTED"); }
    void rejected(JsonNode response,String code) { assertThat(response.path("kind").asText()).as(response.toString()).isEqualTo("ERROR"); assertThat(response.path("code").asText()).isEqualTo(code); }
    int answerCount(long game) { return jdbc.queryForObject("select count(*) from answer where game_session_id=?",Integer.class,game); }

}
