package vn.edu.multigame.realtime.websocket;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.server.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import vn.edu.multigame.auth.security.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.common.config.WebAccessProperties;

@Component
@Profile("mysql")
public class AuthenticatedHandshakeInterceptor implements HandshakeInterceptor {
    public static final String SESSION_ID = "authenticatedHttpSessionId";
    private final AuthService users;
    private final AuthSessionRegistry sessions;
    private final AuthErrorWriter errors;
    private final WebAccessProperties origins;
    public AuthenticatedHandshakeInterceptor(AuthService users, AuthSessionRegistry sessions,
            AuthErrorWriter errors, WebAccessProperties origins) {
        this.users = users; this.sessions = sessions; this.errors = errors; this.origins = origins;
    }
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) throws Exception {
        if (!(request instanceof ServletServerHttpRequest servlet) || !(response instanceof ServletServerHttpResponse output)) return false;
        String origin = request.getHeaders().getOrigin();
        if (origin == null || !origins.allowedOrigins().contains(origin)) {
            errors.write(output.getServletResponse(), 403, "ORIGIN_FORBIDDEN", "Origin không được phép."); return false;
        }
        if (request.getURI().getRawQuery() != null) {
            errors.write(output.getServletResponse(), 400, "INVALID_REQUEST", "WebSocket không nhận query parameter."); return false;
        }
        try {
            Authentication authentication = servlet.getPrincipal() instanceof Authentication auth ? auth : null;
            var user = users.requireActiveUser(authentication);
            var session = servlet.getServletRequest().getSession(false);
            if (session == null) throw AuthService.unauthorized();
            sessions.requireValid(session.getId(), user.getId());
            attributes.put(SESSION_ID, session.getId());
            return true;
        } catch (AuthFailure failure) {
            errors.write(output.getServletResponse(), failure.status().value(), failure.code(), failure.getMessage()); return false;
        } catch (DataAccessException failure) {
            errors.write(output.getServletResponse(), 503, "AUTH_UNAVAILABLE", "Chưa thể xác thực; thử lại sau."); return false;
        }
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {}
}
