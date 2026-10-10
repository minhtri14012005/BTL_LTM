package vn.edu.multigame.auth.security;

import jakarta.servlet.http.HttpSession;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.multigame.auth.service.AuthFailure;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionRegistryTest {
    @Test void revokeImmediatelyRemovesAuthorityButInvalidatesOnlyAfterTransportClose() {
        var close=new CompletableFuture<Void>();var events=mock(ApplicationEventPublisher.class);var session=mock(HttpSession.class);
        when(session.getId()).thenReturn("login");
        doAnswer(call->{((AuthSessionRevoked)call.getArgument(0)).deliveredAfter(close);return null;}).when(events).publishEvent(any(Object.class));
        var registry=new AuthSessionRegistry(events,new ServerProperties());registry.register(session,7);
        registry.revoke("login","LOGGED_OUT");
        assertThatThrownBy(()->registry.requireValid("login",7)).isInstanceOf(AuthFailure.class);
        verify(session).removeAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        verify(session,never()).invalidate();close.complete(null);verify(session).invalidate();
        registry.revoke("login","LOGGED_OUT");verify(events,times(1)).publishEvent(any(Object.class));
    }
    @Test void deferredInvalidationDoesNotDestroyAReauthenticatedRotatedSession() {
        var close=new CompletableFuture<Void>();var events=mock(ApplicationEventPublisher.class);var session=mock(HttpSession.class);
        when(session.getId()).thenReturn("old");
        doAnswer(call->{((AuthSessionRevoked)call.getArgument(0)).deliveredAfter(close);return null;}).when(events).publishEvent(any(Object.class));
        var registry=new AuthSessionRegistry(events,new ServerProperties());registry.register(session,7);registry.revoke("old","SESSION_EXPIRED");
        when(session.getId()).thenReturn("rotated");registry.register(session,7);close.complete(null);verify(session,never()).invalidate();
    }
}
