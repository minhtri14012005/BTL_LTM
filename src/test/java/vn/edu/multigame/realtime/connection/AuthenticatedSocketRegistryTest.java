package vn.edu.multigame.realtime.connection;

import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import vn.edu.multigame.auth.security.*;
import vn.edu.multigame.auth.service.AuthService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticatedSocketRegistryTest {
    @org.junit.jupiter.api.AfterEach void shutdown() {registry.shutdown();}
    @Test void orderedPresenceReplacementAndStaleDisconnectNeverInventOfflineEpisode() {
        var transitions=new ArrayList<AuthenticatedSocketRegistry.PresenceChange>();java.util.function.Consumer<AuthenticatedSocketRegistry.PresenceChange> listener=transitions::add;registry.onPresence(listener);
        var old=socket("old-presence");var fresh=socket("new-presence");registry.add("old-login",old,1);registry.connected(old);
        registry.withPresence(online->assertThat(online).containsExactly(1L));registry.add("new-login",fresh,1);registry.connected(fresh);registry.connected(fresh);
        registry.remove("old-login",old);registry.revoked(new AuthSessionRevoked("old-login","LOGGED_OUT"));
        assertThat(transitions).extracting(AuthenticatedSocketRegistry.PresenceChange::connected).containsExactly(true,true);
        registry.remove("new-login",fresh);registry.remove("new-login",fresh);
        assertThat(transitions).extracting(AuthenticatedSocketRegistry.PresenceChange::connected).containsExactly(true,true,false);
        registry.withPresence(online->assertThat(online).isEmpty());registry.removePresenceListener(listener);
        registry.add("later",socket("later"),1);assertThat(transitions).hasSize(3);
    }
    @Test void registrationAloneDoesNotEndOfflineEpisodeAndStaleConnectionCannotConfirmPresence() {
        var transitions=new ArrayList<AuthenticatedSocketRegistry.PresenceChange>();registry.onPresence(transitions::add);
        var first=socket("unconfirmed");registry.add("first",first,1);
        registry.withPresence(online->assertThat(online).isEmpty());assertThat(transitions).isEmpty();
        var replacement=socket("confirmed");registry.add("second",replacement,1);
        assertThatThrownBy(()->registry.connected(first)).isInstanceOf(SocketReplaced.class);assertThat(transitions).isEmpty();
        registry.connected(replacement);registry.revoked(new AuthSessionRevoked("second","LOGGED_OUT"));
        assertThat(transitions).extracting(AuthenticatedSocketRegistry.PresenceChange::connected).containsExactly(true,false);
    }
    final AuthService users=mock(AuthService.class);
    final AuthenticatedSocketRegistry registry=new AuthenticatedSocketRegistry(new ObjectMapper(),mock(AuthSessionRegistry.class),users);
    WebSocketSession socket(String id) {
        var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn(id);when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());return socket;
    }
    void failedGameBroadcast(RuntimeException failure,CloseStatus expected) throws Exception {
        var socket=socket("broadcast");registry.add("login",socket,1);registry.subscribe(socket,7);
        when(users.requireActiveUser(any())).thenThrow(failure);
        var snapshot=mock(vn.edu.multigame.game.dto.response.GameSnapshot.class);when(snapshot.roomId()).thenReturn(7L);
        registry.gameChanged(new vn.edu.multigame.game.service.GameLifecycleEvent("QUESTION_START",snapshot));
        verify(socket,timeout(2000)).close(expected);verify(socket,never()).sendMessage(any());
        assertThatThrownBy(() -> registry.requireCurrent(socket)).isInstanceOf(SocketReplaced.class);
    }
    @Test void gameBroadcastAuthenticationRejectionUsesExpiredConnectionPolicy() throws Exception {
        failedGameBroadcast(AuthService.unauthorized(),new CloseStatus(4001,"SESSION_EXPIRED"));
    }
    @Test void gameBroadcastDatabaseFailureRemainsInfrastructureError() throws Exception {
        failedGameBroadcast(new org.springframework.dao.DataAccessResourceFailureException("Injected auth DB failure"),new CloseStatus(1011,"AUTH_UNAVAILABLE"));
    }
    @Test void replacementHasIncreasingGenerationAndOldDisconnectCannotRemoveNewSocket() throws Exception {
        var old=socket("old");var fresh=socket("new");long a=registry.add("login-a",old,1);long b=registry.add("login-b",fresh,1);
        assertThat(b).isGreaterThan(a);assertThatThrownBy(() -> registry.requireCurrent(old)).isInstanceOf(SocketReplaced.class);
        verify(old,timeout(2000)).sendMessage(argThat(m -> m.getPayload().toString().contains("SESSION_REPLACED")));
        verify(old,timeout(2000)).close(new CloseStatus(4002,"SESSION_REPLACED"));
        registry.remove("login-a",old);registry.revoked(new AuthSessionRevoked("login-a","LOGGED_OUT"));
        assertThat(registry.requireCurrent(fresh)).isEqualTo(b);registry.send(fresh,Map.of("test",true));verify(fresh,timeout(2000)).sendMessage(any());
        registry.send(old,Map.of("stale",true));verify(old,times(1)).sendMessage(any());
    }
    @Test void logoutRemovesCurrentOnlyAndUsersRemainIndependent() throws Exception {
        var a=socket("a");var b=socket("b");registry.add("session-a",a,1);long gen=registry.add("session-b",b,2);
        registry.revoked(new AuthSessionRevoked("session-a","LOGGED_OUT"));
        assertThatThrownBy(() -> registry.requireCurrent(a)).isInstanceOf(SocketReplaced.class);assertThat(registry.requireCurrent(b)).isEqualTo(gen);
        verify(a,timeout(2000)).close(new CloseStatus(4001,"LOGGED_OUT"));verify(b,never()).close(any());
    }
    @Test void simultaneousConnectionsPublishOnlyOneOwnerAndGeneration() throws Exception {
        var a=socket("a");var b=socket("b");var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> {go.await();return registry.add("a",a,1);});
            var second=pool.submit(() -> {go.await();return registry.add("b",b,1);});go.countDown();
            long x=first.get(5,TimeUnit.SECONDS),y=second.get(5,TimeUnit.SECONDS);
            var winner=x>y?a:b;var loser=x>y?b:a;
            assertThat(registry.requireCurrent(winner)).isEqualTo(Math.max(x,y));
            assertThatThrownBy(() -> registry.requireCurrent(loser)).isInstanceOf(SocketReplaced.class);
            registry.remove(x>y?"b":"a",loser);assertThat(registry.requireCurrent(winner)).isEqualTo(Math.max(x,y));
        }
    }
    @Test void sendFailureDetachesOnlyFailedSocketAndKeepsOtherUserOnline() throws Exception {
        var a=socket("failed-send");var b=socket("healthy-send");
        registry.add("session-a",a,1);registry.connected(a);long generation=registry.add("session-b",b,2);registry.connected(b);
        doThrow(new java.io.IOException("Injected send failure")).when(a).sendMessage(any());
        registry.send(a,Map.of("ack",true));registry.send(b,Map.of("event",true));
        verify(a,timeout(2000)).close(new CloseStatus(1011,"SEND_UNAVAILABLE"));verify(b,timeout(2000)).sendMessage(any());
        assertThat(registry.onlineUsers()).containsExactly(2L);assertThat(registry.requireCurrent(b)).isEqualTo(generation);
        assertThatThrownBy(()->registry.requireCurrent(a)).isInstanceOf(SocketReplaced.class);
        registry.send(a,Map.of("retry",true));verify(a,times(1)).sendMessage(any());
    }
    @Test void replacementRegistrationDoesNotWaitForOldSocketAndOldFailureCannotRemoveNewOwner() throws Exception {
        var old=socket("slow-old");var fresh=socket("fast-new");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        registry.add("old",old,1);registry.connected(old);
        doAnswer(call->{entered.countDown();release.await(3,TimeUnit.SECONDS);throw new java.io.IOException("Late old failure");}).when(old).sendMessage(any());
        try {
            registry.send(old,Map.of("blocked",true));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            long gen=registry.add("new",fresh,1);registry.connected(fresh);registry.send(fresh,Map.of("fresh",true));
            verify(fresh,timeout(2000)).sendMessage(any());release.countDown();verify(old,timeout(2000)).close(new CloseStatus(1011,"SEND_UNAVAILABLE"));
            assertThat(registry.requireCurrent(fresh)).isEqualTo(gen);assertThat(registry.onlineUsers()).containsExactly(1L);
        } finally {release.countDown();}
    }
}
