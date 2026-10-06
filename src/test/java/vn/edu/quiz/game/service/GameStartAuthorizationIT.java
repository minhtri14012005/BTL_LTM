package vn.edu.quiz.game.service;

import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.quiz.realtime.session.RoomBoundary;
import vn.edu.quiz.room.service.RoomService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A real authenticated HTTP request waits for the same Room boundary as WS Start. */
class GameStartAuthorizationIT extends GameNetworkFixture {
    @Autowired RoomBoundary boundary;
    @MockitoSpyBean RoomService authorizationRooms;

    @Test void logoutBeforeRoomAdmissionRejectsStartAndSameRequestCanBeRetriedAfterLogin() throws Exception {
        var f=fixture();var client=new Client(f.host());var input=request(f.room());
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var authorized=new CountDownLatch(1);
        doAnswer(inv -> {var value=inv.callRealMethod();authorized.countDown();return value;})
                .when(authorizationRooms).authorizeReceipt(eq(f.host().id()),eq(f.room().id()),eq("START_GAME"),isNull());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var holder=pool.submit(() -> boundary.read(RoomBoundary.Scope.room(f.room().id()),() -> {
                held.countDown();try {assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new AssertionError(e);}return null;
            }));
            try {
                assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
                var start=client.http.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/rooms/"+f.room().id()+"/start"))
                        .header("Content-Type","application/json").header("X-CSRF-TOKEN",client.csrf)
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(authorized.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(client.call("POST","/api/auth/logout",null).statusCode()).isEqualTo(204);
                release.countDown();holder.get(5,TimeUnit.SECONDS);var response=start.get(10,TimeUnit.SECONDS);
                if(response.statusCode()==200) {long id=json.readTree(response.body()).path("gameSessionId").asLong();gameIds.add(id);installed.add(id);}
                assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
                assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,f.room().id())).isZero();
                assertThat(observer.seen).isEmpty();
            } finally {release.countDown();}
        }
        client.token();assertThat(client.call("POST","/api/auth/login",java.util.Map.of("username",f.host().name(),"password",PASSWORD)).statusCode()).isEqualTo(200);client.token();
        var success=client.call("POST","/api/rooms/"+f.room().id()+"/start",input);assertThat(success.statusCode()).isEqualTo(200);
        long id=json.readTree(success.body()).path("gameSessionId").asLong();gameIds.add(id);installed.add(id);observer.next("DECISION_STARTED",id,1);
        var cancel=envelope("CANCEL_GAME","GAME",id,null,java.util.Map.of());var host=new Wire(f.host());accepted(host.response(cancel));
        var replay=host.client.call("POST","/api/rooms/"+f.room().id()+"/start",input);
        assertThat(replay.statusCode()).isEqualTo(200);assertThat(replay.body()).isEqualTo(success.body());
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,f.room().id())).isEqualTo(1);
    }
}
