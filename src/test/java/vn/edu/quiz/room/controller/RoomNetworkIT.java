package vn.edu.quiz.room.controller;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.quiz.auth.security.AuthSessionRegistry;
import vn.edu.quiz.realtime.session.RoomBoundary;
import vn.edu.quiz.room.service.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP/WS + MySQL; ACTIVE fixtures check Room guards, not a running Game lifecycle. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000"
}) @ActiveProfiles("mysql")
class RoomNetworkIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSessionRegistry sessions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean RoomBoundary boundary;
    @Autowired PlatformTransactionManager transactions;
    List<Client> clients=new ArrayList<>(); List<Socket> sockets=new ArrayList<>();
    List<String> usernames=new ArrayList<>(); List<Long> rooms=new ArrayList<>(),quizzes=new ArrayList<>();
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"CREATE_ROOM","EDIT_ROOM","OPEN_ROOM","CLOSE_ROOM"})
    void queuedRestMutationCannotUseRevokedLogin(String type) throws Exception {
        var author=login();var host=login();long quiz=quiz(author,"PUBLIC");
        JsonNode original=type.equals("CREATE_ROOM")?null:create(host,quiz,"PLAYER",3);
        if(type.equals("CLOSE_ROOM"))original=open(host,original);
        long rid=original==null?0:original.path("id").asLong();String requestId=id();
        var scope=original==null?RoomBoundary.Scope.create(host.uid):RoomBoundary.Scope.room(rid);
        Object body=switch(type) {
            case "CREATE_ROOM" -> Map.of("requestId",requestId,"config",config(quiz,"PLAYER",3));
            case "EDIT_ROOM" -> Map.of("requestId",requestId,"revision",original.path("revision").asLong(),"config",config(quiz,"PLAYER",4));
            default -> Map.of("requestId",requestId,"revision",original.path("revision").asLong());
        };
        String path="/api/rooms"+(rid==0?"":"/"+rid)+(type.equals("OPEN_ROOM")?"/open":type.equals("CLOSE_ROOM")?"/close":"");
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var arrived=new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(inv -> {arrived.countDown();return inv.callRealMethod();}).when(boundary)
                .mutate(org.mockito.ArgumentMatchers.eq(scope),org.mockito.ArgumentMatchers.eq(host.uid),org.mockito.ArgumentMatchers.eq(requestId),
                        org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var holder=pool.submit(() -> boundary.read(scope,() -> {held.countDown();try {assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new AssertionError(e);}return null;}));
            try {
                assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
                var pending=host.http.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json")
                        .header("X-CSRF-TOKEN",host.csrf).method(type.equals("EDIT_ROOM")?"PUT":"POST",HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(arrived.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(host.request("POST","/api/auth/logout",null,true).statusCode()).isEqualTo(204);
                release.countDown();holder.get(5,TimeUnit.SECONDS);var response=pending.get(10,TimeUnit.SECONDS);
                if(type.equals("CREATE_ROOM") && response.statusCode()==201)rooms.add(json.readTree(response.body()).path("id").asLong());
                error(response,401,"UNAUTHENTICATED");
                if(original==null)assertThat(jdbc.queryForObject("select count(*) from room where host_user_id=?",Integer.class,host.uid)).isZero();
                else {assertThat(jdbc.queryForObject("select revision from room where id=?",Long.class,rid)).isEqualTo(original.path("revision").asLong());
                    assertThat(jdbc.queryForObject("select status from room where id=?",String.class,rid)).isEqualTo(original.path("status").asText());}
            } finally {release.countDown();}
        }
    }
    class Client {
        CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        String csrf; long uid;
        HttpResponse<String> request(String method,String path,Object body,boolean token) throws Exception {
            return raw(method,path,body==null?null:json.writeValueAsString(body),token);
        }
        HttpResponse<String> raw(String method,String path,String body,boolean token) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
            if(token) b.header("X-CSRF-TOKEN",csrf);
            if(body!=null) b.header("Content-Type","application/json");
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        void token() throws Exception { csrf=json.readTree(request("GET","/api/auth/csrf",null,false).body()).get("token").asText(); }
    }
    class Socket implements WebSocket.Listener {
        WebSocket ws; BlockingQueue<JsonNode> messages=new LinkedBlockingQueue<>(); StringBuilder partial=new StringBuilder();
        Socket(Client client) throws Exception {
            ws=client.http.newWebSocketBuilder().header("Origin","http://localhost:8080")
                    .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),this).get(5,TimeUnit.SECONDS); sockets.add(this);
            assertThat(next("AUTH_READY").get("payload").get("userId").asLong()).isEqualTo(client.uid);
        }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last) {
            partial.append(data);
            if(last) { try { messages.add(json.readTree(partial.toString())); } catch(Exception e) { throw new AssertionError(e); } partial.setLength(0); }
            socket.request(1); return null;
        }
        void send(Object command) throws Exception { ws.sendText(json.writeValueAsString(command),true).get(5,TimeUnit.SECONDS); }
        JsonNode next(String kindOrType) throws Exception {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<until) {
                JsonNode n=messages.poll(Math.max(1,until-System.nanoTime()),TimeUnit.NANOSECONDS);
                if(n==null) break;
                if(n.path("kind").asText().equals(kindOrType) || n.path("type").asText().equals(kindOrType)) return n;
            }
            throw new AssertionError("Missing WS "+kindOrType);
        }
        JsonNode ack(Object c) throws Exception { send(c); return next("ACK"); }
        void drain() { messages.clear(); }
    }
    String id() { return UUID.randomUUID().toString(); }
    Client login() throws Exception {
        Client c=new Client(); clients.add(c); c.token(); String name="room_"+id().replace("-","").substring(0,15); usernames.add(name);
        var register=c.request("POST","/api/auth/register",Map.of("username",name,"displayName",name,"password","Room-test-42"),true);
        assertThat(register.statusCode()).as(register.body()).isEqualTo(201); c.uid=json.readTree(register.body()).get("id").asLong();
        assertThat(c.request("POST","/api/auth/login",Map.of("username",name,"password","Room-test-42"),true).statusCode()).isEqualTo(200); c.token(); return c;
    }
    long quiz(Client owner,String visibility) throws Exception {
        var q=Map.of("content","Secret correct question","options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","D");
        var response=owner.request("POST","/api/quizzes",Map.of("title","Room Quiz","visibility",visibility,"questions",Collections.nCopies(10,q)),true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201); long quizId=json.readTree(response.body()).get("id").asLong(); quizzes.add(quizId);return quizId;
    }
    Map<String,Object> config(long quizId,String participation,int maxPlayers) { return new LinkedHashMap<>(Map.of("quizId",quizId,"name","Waiting test","maxPlayers",maxPlayers,"questionDurationMs",10000,"hostParticipation",participation)); }
    JsonNode create(Client host,long quizId,String participation,int maxPlayers) throws Exception {
        var response=host.request("POST","/api/rooms",Map.of("requestId",id(),"config",config(quizId,participation,maxPlayers)),true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201); var room=json.readTree(response.body()); rooms.add(room.get("id").asLong());return room;
    }
    JsonNode open(Client host,JsonNode room) throws Exception {
        var response=host.request("POST","/api/rooms/"+room.get("id").asLong()+"/open",Map.of("requestId",id(),"revision",room.get("revision").asLong()),true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());
    }
    Map<String,Object> command(String type,long roomId,String requestId,Object payload) {
        var command=new LinkedHashMap<String,Object>(); command.put("v",1);command.put("kind","COMMAND");command.put("requestId",requestId);
        command.put("type",type);command.put("target",Map.of("kind","ROOM","id",roomId));command.put("questionIndex",null);command.put("payload",payload);return command;
    }
    Map<String,Object> join(JsonNode room,String requestId,String participation) { return command("JOIN_ROOM",room.get("id").asLong(),requestId,Map.of("roomCode",room.get("roomCode").asText(),"participation",participation)); }
    void error(HttpResponse<String> response,int status,String code) throws Exception { assertThat(response.statusCode()).as(response.body()).isEqualTo(status);assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo(code); }
    void wsError(Socket socket,Object c,String code) throws Exception {socket.send(c);assertThat(socket.next("ERROR").get("code").asText()).isEqualTo(code);}
    @AfterEach void cleanup() {
        sockets.forEach(s->s.ws.abort());
        clients.forEach(c->c.cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("JSESSIONID")).forEach(cookie->sessions.revoke(cookie.getValue(),"LOGGED_OUT")));
        for(long room:rooms) {jdbc.update("DELETE FROM room_member WHERE room_id=?",room);jdbc.update("DELETE FROM room WHERE id=?",room);}
        for(long quiz:quizzes) {jdbc.update("DELETE FROM question WHERE quiz_id=?",quiz);jdbc.update("DELETE FROM quiz WHERE id=?",quiz);}
        usernames.forEach(name->jdbc.update("DELETE FROM app_user WHERE username=?",name)); clients.forEach(c->c.http.close());
    }
    @Test void multipleDraftsOwnerScopeValidationAndCreateDedup() throws Exception {
        Client author=login(),host=login(),outsider=login(); long q=quiz(author,"PUBLIC");
        JsonNode first=create(host,q,"PLAYER",3),second=create(host,q,"SPECTATOR",5);long rid=first.get("id").asLong();
        assertThat(first.get("status").asText()).isEqualTo("DRAFT");assertThat(first.get("members").size()).isEqualTo(1);
        assertThat(first.get("members").get(0).get("userId").asLong()).isEqualTo(host.uid);
        error(outsider.request("GET","/api/rooms/"+rid,null,false),403,"FORBIDDEN");
        assertThat(json.readTree(host.request("GET","/api/rooms",null,false).body()).get("items").size()).isEqualTo(2);
        assertThat(json.readTree(outsider.request("GET","/api/rooms",null,false).body()).get("items").size()).isZero();
        error(outsider.request("PUT","/api/rooms/"+rid,Map.of("requestId",id(),"revision",0,"config",config(q,"PLAYER",3)),true),403,"FORBIDDEN");
        error(host.request("POST","/api/rooms/"+rid+"/open",Map.of("requestId",id(),"revision",0),false),403,"CSRF_INVALID");
        var invalid=config(q,"PLAYER",2);error(host.request("POST","/api/rooms",Map.of("requestId",id(),"config",invalid),true),400,"INVALID_REQUEST");
        invalid=config(q,"PLAYER",3);invalid.put("userId",outsider.uid);error(host.request("POST","/api/rooms",Map.of("requestId",id(),"config",invalid),true),400,"INVALID_REQUEST");
        invalid=config(q,"PLAYER",3);invalid.put("questionDurationMs",0);error(host.request("POST","/api/rooms",Map.of("requestId",id(),"config",invalid),true),400,"INVALID_REQUEST");
        invalid=config(q,"PLAYER",3);invalid.put("questionDurationMs",1.5);error(host.request("POST","/api/rooms",Map.of("requestId",id(),"config",invalid),true),400,"INVALID_REQUEST");
        String valid=json.writeValueAsString(Map.of("requestId",id(),"config",config(q,"PLAYER",3)));
        error(host.raw("POST","/api/rooms",valid+"{}",true),400,"INVALID_REQUEST");
        error(host.raw("POST","/api/rooms",valid.substring(0,valid.length()-1)+",\"requestId\":\""+id()+"\"}",true),400,"INVALID_REQUEST");
        var request=Map.of("requestId",id(),"config",config(q,"PLAYER",3));
        var a=host.request("POST","/api/rooms",request,true);var b=host.request("POST","/api/rooms",request,true);
        assertThat(a.statusCode()).isEqualTo(201);assertThat(b.body()).isEqualTo(a.body());rooms.add(json.readTree(a.body()).get("id").asLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room WHERE host_user_id=?",Integer.class,host.uid)).isEqualTo(3);
        error(host.request("POST","/api/rooms",Map.of("requestId",request.get("requestId"),"config",config(q,"SPECTATOR",3)),true),409,"INVALID_REQUEST_ID");
        Client anonymous=new Client();clients.add(anonymous);error(anonymous.request("GET","/api/rooms",null,false),401,"UNAUTHENTICATED");
        assertThat(second.toString()).doesNotContain("correctAnswer","options","Secret correct", "password");
    }
    @Test void joinBroadcastLeaveRejoinRemoveAndSnapshotArePrivateToJoinedMembers() throws Exception {
        Client author=login(),host=login(),player=login(),outsider=login();JsonNode room=open(host,create(host,quiz(author,"PUBLIC"),"PLAYER",3));long rid=room.get("id").asLong();
        Socket h=new Socket(host),p=new Socket(player),o=new Socket(outsider);
        h.ack(command("SUBSCRIBE_ROOM",rid,id(),Map.of()));
        var preview=outsider.request("GET","/api/rooms/by-code/"+room.get("roomCode").asText(),null,false);
        assertThat(preview.statusCode()).isEqualTo(200);assertThat(preview.body()).doesNotContain("members","quiz", "correct");
        var joined=p.ack(join(room,id(),"PLAYER"));long memberId=joined.get("payload").get("members").get(1).get("membershipId").asLong();
        var event=h.next("ROOM_UPDATED");assertThat(event.get("revision").asLong()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT revision FROM room WHERE id=?",Long.class,rid)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_member WHERE room_id=? AND status='JOINED'",Integer.class,rid)).isEqualTo(2);
        assertThat(event.toString()).doesNotContain("correctAnswer","options","Secret correct","password");
        assertThat(o.messages.poll(200,TimeUnit.MILLISECONDS)).isNull();
        wsError(o,command("SUBSCRIBE_ROOM",rid,id(),Map.of()),"FORBIDDEN");error(outsider.request("GET","/api/rooms/"+rid,null,false),403,"FORBIDDEN");
        var leave=command("LEAVE_ROOM",rid,id(),Map.of());assertThat(p.ack(leave).get("payload").toString()).isEqualTo("{\"left\":true}");
        assertThat(p.ack(leave).get("revision").asLong()).isEqualTo(3); // Replay after membership becomes LEFT.
        error(player.request("GET","/api/rooms/"+rid,null,false),403,"FORBIDDEN");
        var again=p.ack(join(room,id(),"SPECTATOR"));
        assertThat(again.get("payload").get("members").get(1).get("membershipId").asLong()).isEqualTo(memberId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_member WHERE room_id=? AND user_id=?",Integer.class,rid,player.uid)).isEqualTo(1);
        wsError(p,command("REMOVE_MEMBER",rid,id(),Map.of("userId",host.uid)),"FORBIDDEN");
        p.drain();h.ack(command("REMOVE_MEMBER",rid,id(),Map.of("userId",player.uid)));
        var revoked=p.next("ROOM_ACCESS_REVOKED");assertThat(revoked.get("payload").toString()).doesNotContain("members", "quizTitle");
        wsError(p,command("SUBSCRIBE_ROOM",rid,id(),Map.of()),"FORBIDDEN");
        // Remove is not a ban; the same retained membership can join again while WAITING.
        assertThat(p.ack(join(room,id(),"PLAYER")).get("payload").get("members").size()).isEqualTo(2);
        wsError(h,command("LEAVE_ROOM",rid,id(),Map.of()),"HOST_CANNOT_LEAVE");
    }
    @Test void privateQuizAuthorGuardAndParticipationChangeBeforeStart() throws Exception {
        Client author=login(),host=login(),player=login();long privateQuiz=quiz(author,"PRIVATE");
        error(host.request("POST","/api/rooms",Map.of("requestId",id(),"config",config(privateQuiz,"SPECTATOR",3)),true),404,"QUIZ_NOT_FOUND");
        error(author.request("POST","/api/rooms",Map.of("requestId",id(),"config",config(privateQuiz,"PLAYER",3)),true),403,"QUIZ_AUTHOR_CANNOT_PLAY");
        JsonNode room=open(author,create(author,privateQuiz,"SPECTATOR",3));Socket a=new Socket(author),p=new Socket(player);
        a.ack(command("SUBSCRIBE_ROOM",room.get("id").asLong(),id(),Map.of()));
        assertThat(p.ack(join(room,id(),"PLAYER")).get("payload").get("members").size()).isEqualTo(2);
        wsError(a,join(room,id(),"PLAYER"),"QUIZ_AUTHOR_CANNOT_PLAY");
        error(player.request("GET","/api/quizzes/"+privateQuiz,null,false),404,"QUIZ_NOT_FOUND");
        long publicQuiz=quiz(author,"PUBLIC");JsonNode other=create(host,publicQuiz,"PLAYER",3);
        var edited=host.request("PUT","/api/rooms/"+other.get("id").asLong(),Map.of("requestId",id(),"revision",0,"config",config(publicQuiz,"SPECTATOR",3)),true);
        assertThat(edited.statusCode()).as(edited.body()).isEqualTo(200);assertThat(json.readTree(edited.body()).get("revision").asLong()).isEqualTo(1);
        assertThat(json.readTree(edited.body()).get("members").get(0).get("participation").asText()).isEqualTo("SPECTATOR");
    }
    @Test void simultaneousLastSlotJoinSerializesAndSameIdAcrossRoomsIsIndependent() throws Exception {
        Client author=login(),host=login(),p1=login(),p2=login(),p3=login();long q=quiz(author,"PUBLIC");
        JsonNode room=open(host,create(host,q,"PLAYER",3));long rid=room.get("id").asLong();
        Socket one=new Socket(p1),two=new Socket(p2),three=new Socket(p3);one.ack(join(room,id(),"PLAYER"));
        two.send(join(room,id(),"PLAYER"));three.send(join(room,id(),"PLAYER"));
        var t=two.messages.poll(5,TimeUnit.SECONDS);var u=three.messages.poll(5,TimeUnit.SECONDS);
        assertThat(t).isNotNull();assertThat(u).isNotNull();assertThat(List.of(t.get("kind").asText(),u.get("kind").asText())).containsExactlyInAnyOrder("ACK","ERROR");
        assertThat((t.get("kind").asText().equals("ERROR")?t:u).get("code").asText()).isEqualTo("ROOM_FULL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_member WHERE room_id=? AND status='JOINED' AND participation='PLAYER'",Integer.class,rid)).isEqualTo(3);
        JsonNode other=open(host,create(host,q,"SPECTATOR",3));String sameId=id();
        one.ack(join(room,sameId,"SPECTATOR"));assertThat(one.ack(join(other,sameId,"PLAYER")).get("target").get("id").asLong()).isEqualTo(other.get("id").asLong());
        wsError(one,join(room,sameId,"PLAYER"),"INVALID_REQUEST_ID");
    }
    @Test void duplicateAcrossSocketReplacementCommitsOneMembershipAndRetryGetsFreshSnapshot() throws Exception {
        Client author=login(),host=login(),player=login();JsonNode room=open(host,create(host,quiz(author,"PUBLIC"),"PLAYER",3));long rid=room.get("id").asLong();
        Socket p1=new Socket(player),h=new Socket(host);h.ack(command("SUBSCRIBE_ROOM",rid,id(),Map.of()));
        Object request=join(room,id(),"PLAYER");JsonNode a=p1.ack(request);
        Socket p2=new Socket(player);assertThat(p1.next("SESSION_REPLACED").path("payload").path("replacementGeneration").asLong()).isPositive();
        JsonNode b=p2.ack(request);assertThat(a).isEqualTo(b);
        assertThat(jdbc.queryForObject("SELECT revision FROM room WHERE id=?",Long.class,rid)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_member WHERE room_id=? AND user_id=?",Integer.class,rid,player.uid)).isEqualTo(1);
        var conf=config(room.get("quizId").asLong(),"SPECTATOR",3);
        var edit=host.request("PUT","/api/rooms/"+rid,Map.of("requestId",id(),"revision",2,"config",conf),true);assertThat(edit.statusCode()).as(edit.body()).isEqualTo(200);
        p2.drain();p2.send(request);assertThat(p2.next("ACK")).isEqualTo(a);
        assertThat(p2.next("ROOM_UPDATED").get("revision").asLong()).isEqualTo(3); // Fresh private snapshot after cached ACK on current connection.
    }
    @Test void closeTerminalAndActiveRoomMutationsCannotChangeRosterOrConfig() throws Exception {
        Client author=login(),host=login(),player=login();JsonNode room=open(host,create(host,quiz(author,"PUBLIC"),"PLAYER",3));long rid=room.get("id").asLong();
        Socket h=new Socket(host),p=new Socket(player);h.ack(command("SUBSCRIBE_ROOM",rid,id(),Map.of()));p.ack(join(room,id(),"PLAYER"));
        jdbc.update("UPDATE room SET status='ACTIVE',revision=revision+1 WHERE id=?",rid); // Fixture only; no Start endpoint exists.
        var version=Map.of("requestId",id(),"revision",3);
        error(host.request("POST","/api/rooms/"+rid+"/close",version,true),409,"INVALID_STATE");
        error(host.request("POST","/api/rooms/"+rid+"/open",version,true),409,"INVALID_STATE");
        error(host.request("PUT","/api/rooms/"+rid,Map.of("requestId",id(),"revision",3,"config",config(room.get("quizId").asLong(),"SPECTATOR",3)),true),409,"INVALID_STATE");
        wsError(p,join(room,id(),"SPECTATOR"),"INVALID_STATE");wsError(p,command("LEAVE_ROOM",rid,id(),Map.of()),"INVALID_STATE");
        wsError(h,command("REMOVE_MEMBER",rid,id(),Map.of("userId",player.uid)),"INVALID_STATE");
        assertThat(jdbc.queryForObject("SELECT participation FROM room_member WHERE room_id=? AND user_id=?",String.class,rid,host.uid)).isEqualTo("PLAYER");
        jdbc.update("UPDATE room SET status='WAITING',revision=revision+1 WHERE id=?",rid);
        p.drain();
        var close=Map.of("requestId",id(),"revision",4);var closed=host.request("POST","/api/rooms/"+rid+"/close",close,true);
        assertThat(closed.statusCode()).as(closed.body()).isEqualTo(200);assertThat(json.readTree(closed.body()).get("status").asText()).isEqualTo("CLOSED");
        assertThat(host.request("POST","/api/rooms/"+rid+"/close",close,true).body()).isEqualTo(closed.body());
        assertThat(p.next("ROOM_UPDATED").get("payload").get("status").asText()).isEqualTo("CLOSED");
        error(host.request("POST","/api/rooms/"+rid+"/open",Map.of("requestId",id(),"revision",5),true),409,"INVALID_STATE");
        wsError(p,join(room,id(),"PLAYER"),"INVALID_STATE");
        assertThat(player.request("GET","/api/rooms/"+rid,null,false).statusCode()).isEqualTo(200); // Frozen final roster remains visible.
    }
    @Test void invalidIdentityPayloadAndConcurrentEditUseOneRevisionBoundary() throws Exception {
        Client author=login(),host=login(),player=login();JsonNode room=open(host,create(host,quiz(author,"PUBLIC"),"PLAYER",3));long rid=room.get("id").asLong();Socket p=new Socket(player);
        var forged=join(room,id(),"PLAYER");forged.put("userId",host.uid);wsError(p,forged,"INVALID_MESSAGE");
        var invalid=join(room,id(),"PLAYER");invalid.put("questionIndex",1);wsError(p,invalid,"INVALID_MESSAGE");
        var wrongCode=command("JOIN_ROOM",rid,id(),Map.of("roomCode","ZZZZZZZZZZZZ","participation","PLAYER"));wsError(p,wrongCode,"ROOM_NOT_FOUND");
        var cfg=config(room.get("quizId").asLong(),"PLAYER",3);
        Object first=Map.of("requestId",id(),"revision",1,"config",cfg),second=Map.of("requestId",id(),"revision",1,"config",cfg);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->host.request("PUT","/api/rooms/"+rid,first,true));var b=pool.submit(()->host.request("PUT","/api/rooms/"+rid,second,true));
            assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT revision FROM room WHERE id=?",Long.class,rid)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_session WHERE room_id=?",Integer.class,rid)).isZero();
    }
    @Test void realMysqlRollbackCannotPublishOrAckAndBoundaryIsFenced() throws Exception {
        Client author=login(),host=login();JsonNode room=create(host,quiz(author,"PUBLIC"),"PLAYER",3);long rid=room.get("id").asLong();
        Socket h=new Socket(host);h.ack(command("SUBSCRIBE_ROOM",rid,id(),Map.of()));
        TransactionTemplate tx=new TransactionTemplate(transactions);
        assertThatThrownBy(()->boundary.mutate(RoomBoundary.Scope.room(rid),host.uid,id(),"failure",()->{},()->tx.execute(status->{
            jdbc.update("UPDATE room SET name='must rollback',revision=revision+1 WHERE id=?",rid);
            jdbc.update("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES (?,?,'PLAYER','JOINED',?)",rid,Long.MAX_VALUE,System.currentTimeMillis());
            throw new AssertionError("FK must reject");
        }),(r,replay)->{throw new AssertionError("must not ACK");})).isInstanceOf(RoomFailure.class).hasMessage("ROOM_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT name FROM room WHERE id=?",String.class,rid)).isEqualTo("Waiting test");
        assertThat(jdbc.queryForObject("SELECT revision FROM room WHERE id=?",Long.class,rid)).isEqualTo(0L);
        assertThat(h.messages.poll(200,TimeUnit.MILLISECONDS)).isNull();
        error(host.request("POST","/api/rooms/"+rid+"/open",Map.of("requestId",id(),"revision",0),true),503,"ROOM_UNAVAILABLE");
        assertThat(host.request("GET","/api/rooms/"+rid,null,false).statusCode()).isEqualTo(200);
    }
    @Test void restConfigAndWsJoinRaceShareTheSameAggregateBoundary() throws Exception {
        Client author=login(),host=login(),player=login();JsonNode room=open(host,create(host,quiz(author,"PUBLIC"),"PLAYER",3));long rid=room.get("id").asLong();
        Socket h=new Socket(host),p=new Socket(player);h.ack(command("SUBSCRIBE_ROOM",rid,id(),Map.of()));
        var config=config(room.get("quizId").asLong(),"SPECTATOR",3);
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch go=new CountDownLatch(1);
            var edit=pool.submit(()->{go.await();return host.request("PUT","/api/rooms/"+rid,Map.of("requestId",id(),"revision",1,"config",config),true);});
            var join=pool.submit(()->{go.await();return p.ack(join(room,id(),"PLAYER"));});go.countDown();
            assertThat(join.get(5,TimeUnit.SECONDS).get("kind").asText()).isEqualTo("ACK");
            var response=edit.get(5,TimeUnit.SECONDS);assertThat(response.statusCode()).isIn(200,409);
            long expected=response.statusCode()==200?3:2;
            JsonNode snapshot=json.readTree(host.request("GET","/api/rooms/"+rid,null,false).body());
            assertThat(snapshot.get("revision").asLong()).isEqualTo(expected);assertThat(snapshot.get("members").size()).isEqualTo(2);
            List<Long> eventRevisions=new ArrayList<>();
            eventRevisions.add(h.next("ROOM_UPDATED").get("revision").asLong());
            if(response.statusCode()==200) eventRevisions.add(h.next("ROOM_UPDATED").get("revision").asLong());
            assertThat(eventRevisions).isSorted();assertThat(eventRevisions.getLast()).isEqualTo(expected);
        }
    }
}
