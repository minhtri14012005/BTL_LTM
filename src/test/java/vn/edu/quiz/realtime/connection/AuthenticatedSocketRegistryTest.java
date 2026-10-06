package vn.edu.quiz.realtime.connection;

import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import vn.edu.quiz.auth.security.*;
import vn.edu.quiz.auth.service.AuthService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticatedSocketRegistryTest {
    final AuthService users=mock(AuthService.class);
    final AuthenticatedSocketRegistry registry=new AuthenticatedSocketRegistry(new ObjectMapper(),mock(AuthSessionRegistry.class),users);
    WebSocketSession socket(String id) {
        var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn(id);when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());return socket;
    }
    void failedGameBroadcast(RuntimeException failure,CloseStatus expected) throws Exception {
        var socket=socket("broadcast");registry.add("login",socket,1);registry.subscribe(socket,7);
        when(users.requireActiveUser(any())).thenThrow(failure);
        var snapshot=mock(vn.edu.quiz.game.dto.response.GameSnapshot.class);when(snapshot.roomId()).thenReturn(7L);
        registry.gameChanged(new vn.edu.quiz.game.service.GameLifecycleEvent("QUESTION_START",snapshot));
        verify(socket).close(expected);verify(socket,never()).sendMessage(any());
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
        verify(old).sendMessage(argThat(m -> m.getPayload().toString().contains("SESSION_REPLACED")));
        verify(old).close(new CloseStatus(4002,"SESSION_REPLACED"));
        registry.remove("login-a",old);registry.revoked(new AuthSessionRevoked("login-a","LOGGED_OUT"));
        assertThat(registry.requireCurrent(fresh)).isEqualTo(b);registry.send(fresh,Map.of("test",true));verify(fresh).sendMessage(any());
        registry.send(old,Map.of("stale",true));verify(old,times(1)).sendMessage(any());
    }
    @Test void logoutRemovesCurrentOnlyAndUsersRemainIndependent() throws Exception {
        var a=socket("a");var b=socket("b");registry.add("session-a",a,1);long gen=registry.add("session-b",b,2);
        registry.revoked(new AuthSessionRevoked("session-a","LOGGED_OUT"));
        assertThatThrownBy(() -> registry.requireCurrent(a)).isInstanceOf(SocketReplaced.class);assertThat(registry.requireCurrent(b)).isEqualTo(gen);
        verify(a).close(new CloseStatus(4001,"LOGGED_OUT"));verify(b,never()).close(any());
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
}
