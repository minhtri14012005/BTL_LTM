package vn.edu.multigame.realtime.websocket;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.multigame.realtime.message.event.AuthReady;

/** Frozen authentication transport, routed to Waiting and Game adapters on the same socket. */
@Component
@Profile("mysql")
public class AuthenticatedWebSocketHandler extends AbstractWebSocketHandler {
    private final AuthService users;
    private final AuthSessionRegistry sessions;
    private final AuthenticatedSocketRegistry sockets;
    private final WaitingRoomAdapter waiting;
    public AuthenticatedWebSocketHandler(AuthService users, AuthSessionRegistry sessions,
            AuthenticatedSocketRegistry sockets,WaitingRoomAdapter waiting) {
        this.users = users; this.sessions = sessions; this.sockets = sockets; this.waiting=waiting;
    }
    private String sessionId(WebSocketSession socket) {
        return (String) socket.getAttributes().get(AuthenticatedHandshakeInterceptor.SESSION_ID);
    }
    private vn.edu.multigame.user.entity.UserAccount validate(WebSocketSession socket) {
        sockets.requireCurrent(socket);
        var authentication = socket.getPrincipal() instanceof Authentication auth ? auth : null;
        var user = users.requireActiveUser(authentication);
        sessions.requireValid(sessionId(socket), user.getId());
        return user;
    }
    @Override public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
        socket.setTextMessageSizeLimit(8192); socket.setBinaryMessageSizeLimit(8192);
        try {
            var authentication=socket.getPrincipal() instanceof Authentication auth?auth:null;
            var initial=users.requireActiveUser(authentication);
            sessions.requireValid(sessionId(socket),initial.getId());
            long generation=sockets.add(sessionId(socket),socket,initial.getId());
            // Recheck after registration: logout could occur between handshake and connection establishment.
            var user = validate(socket);
            if (socket.isOpen()) sockets.send(socket,AuthReady.of(user.getId(), user.getUsername(),generation));
        } catch (AuthFailure failure) { socket.close(new CloseStatus(4001, "SESSION_EXPIRED")); }
        catch(vn.edu.multigame.realtime.connection.SocketReplaced failure) { socket.close(new CloseStatus(4002,"SESSION_REPLACED")); }
        catch (DataAccessException failure) { socket.close(new CloseStatus(1011, "AUTH_UNAVAILABLE")); }
    }
    @Override protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws Exception {
        try { validate(socket); waiting.text(socket,message.getPayload(),()->validate(socket)); }
        catch(WaitingRoomCommandParser.Unsupported e) { socket.close(new CloseStatus(1008,"UNSUPPORTED_MESSAGE")); }
        catch(AuthFailure e) { socket.close(new CloseStatus(4001,"SESSION_EXPIRED")); }
        catch(vn.edu.multigame.realtime.connection.SocketReplaced e) { socket.close(new CloseStatus(4002,"SESSION_REPLACED")); }
        catch(DataAccessException e) { socket.close(new CloseStatus(1011,"AUTH_UNAVAILABLE")); }
    }
    @Override protected void handleBinaryMessage(WebSocketSession socket, BinaryMessage message) throws Exception { unsupported(socket); }
    private void unsupported(WebSocketSession socket) throws Exception {
        try { validate(socket); socket.close(new CloseStatus(1008, "UNSUPPORTED_MESSAGE")); }
        catch (AuthFailure failure) { socket.close(new CloseStatus(4001, "SESSION_EXPIRED")); }
        catch(vn.edu.multigame.realtime.connection.SocketReplaced failure) { socket.close(new CloseStatus(4002,"SESSION_REPLACED")); }
        catch (DataAccessException failure) { socket.close(new CloseStatus(1011, "AUTH_UNAVAILABLE")); }
    }
    @Override public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        sockets.remove(sessionId(socket), socket);
    }
}
