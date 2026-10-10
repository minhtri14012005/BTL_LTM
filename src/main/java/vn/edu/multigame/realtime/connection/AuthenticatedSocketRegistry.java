package vn.edu.multigame.realtime.connection;

import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import vn.edu.multigame.auth.security.AuthSessionRevoked;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.auth.service.AuthFailure;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import vn.edu.multigame.room.service.RoomChanged;
import vn.edu.multigame.room.enums.RoomStatus;
import vn.edu.multigame.realtime.message.common.RoomTarget;
import vn.edu.multigame.realtime.message.event.RoomEvent;

@Component
@Profile("mysql")
public class AuthenticatedSocketRegistry {
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sockets = new ConcurrentHashMap<>();
    private record Binding(String sessionId,long userId,long generation,WebSocketSession outbound,Set<Long> rooms) {}
    private final Map<String,Binding> bindings=new ConcurrentHashMap<>();
    private final Map<Long,Binding> active=new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong generations=new java.util.concurrent.atomic.AtomicLong();
    private final ObjectMapper json;
    private final AuthSessionRegistry sessions;
    private final AuthService users;
    public AuthenticatedSocketRegistry(ObjectMapper json,AuthSessionRegistry sessions,AuthService users) {
        this.json=json; this.sessions=sessions; this.users=users;
    }
    public long add(String sessionId, WebSocketSession socket,long userId) {
        // Tomcat timeout is finite even if a lone synchronous send stalls; decorator bounds concurrent sends.
        socket.getAttributes().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT",5000L);
        var replaced=new java.util.concurrent.atomic.AtomicReference<Binding>();
        var binding=active.compute(userId,(uid,previous) -> {
            var created=new Binding(sessionId,userId,generations.incrementAndGet(),new ConcurrentWebSocketSessionDecorator(socket,5000,262144),ConcurrentHashMap.newKeySet());
            bindings.put(socket.getId(),created);
            sockets.compute(sessionId,(id,existing) -> {
                Set<WebSocketSession> set=existing==null?ConcurrentHashMap.newKeySet():existing;
                set.add(socket); return set;
            });
            replaced.set(previous); return created;
        });
        var previous=replaced.get();
        if(previous!=null) {
            // Notification bypasses the normal current-generation send guard.
            try { previous.outbound().sendMessage(new TextMessage(json.writeValueAsString(
                    vn.edu.multigame.realtime.message.event.SessionReplaced.of(previous.generation(),binding.generation())))); }
            catch(IOException | RuntimeException ignored) {}
            remove(previous.sessionId(),previous.outbound());
            try { previous.outbound().close(new CloseStatus(4002,"SESSION_REPLACED")); } catch(IOException ignored) {}
        }
        return binding.generation();
    }
    /** A single sampled set; gameplay freezes its wait-set at question open. Replacement does not remove the new binding. */
    public Set<Long> onlineUsers() {
        return active.entrySet().stream().filter(e -> e.getValue().outbound().isOpen()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    /** Linearization point for permission to begin an operation; running transactions may finish. */
    public long requireCurrent(WebSocketSession socket) {
        var binding=bindings.get(socket.getId());
        if(binding==null || active.get(binding.userId())!=binding) throw new SocketReplaced();
        return binding.generation();
    }
    public void remove(String sessionId, WebSocketSession socket) {
        var binding=bindings.remove(socket.getId());
        if(binding!=null) active.remove(binding.userId(),binding);
        sockets.computeIfPresent(sessionId, (id, existing) -> { existing.removeIf(s->s.getId().equals(socket.getId())); return existing.isEmpty() ? null : existing; });
    }
    @EventListener
    public void revoked(AuthSessionRevoked event) {
        Set<WebSocketSession> set = sockets.remove(event.sessionId());
        if (set == null) return;
        for (WebSocketSession socket : set) {
            var binding=bindings.remove(socket.getId());
            if(binding!=null) active.remove(binding.userId(),binding);
            try { socket.close(new CloseStatus(4001, event.reason())); }
            catch (IOException ignored) { /* Connection already gone; remaining sockets must still close. */ }
        }
    }
    public void subscribe(WebSocketSession socket,long roomId) {
        Binding binding=bindings.get(socket.getId());
        if(binding==null) return;
        requireSubscriptionCapacity(socket,roomId);
        binding.rooms().add(roomId);
    }
    public void requireSubscriptionCapacity(WebSocketSession socket,long roomId) {
        Binding binding=bindings.get(socket.getId()); if(binding==null) return;
        if(!binding.rooms().contains(roomId) && binding.rooms().size()>=32)
            throw new vn.edu.multigame.room.service.RoomFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"SUBSCRIPTION_LIMIT_REACHED");
    }
    public void unsubscribe(WebSocketSession socket,long roomId) {
        Binding binding=bindings.get(socket.getId()); if(binding!=null) binding.rooms().remove(roomId);
    }
    /** Network failure closes this connection, never rolls back or repeats a committed operation. */
    public void send(WebSocketSession socket,Object message) {
        Binding binding=bindings.get(socket.getId()); if(binding==null) return;
        if(active.get(binding.userId())!=binding) return;
        try { if(binding.outbound().isOpen()) binding.outbound().sendMessage(new TextMessage(json.writeValueAsString(message))); }
        catch(IOException | RuntimeException failure) {
            remove(binding.sessionId(),socket);
            try { socket.close(new CloseStatus(1011,"SEND_UNAVAILABLE")); } catch(IOException ignored) {}
        }
    }
    @EventListener
    public void roomChanged(RoomChanged changed) {
        var room=changed.snapshot();
        String eventId=UUID.randomUUID().toString(); long now=System.currentTimeMillis();
        for(Binding binding:bindings.values()) {
            if(!binding.rooms().contains(room.id())) continue;
            WebSocketSession socket=binding.outbound();
            try {
                var user=users.requireActiveUser(socket.getPrincipal() instanceof Authentication auth?auth:null);
                sessions.requireValid(binding.sessionId(),user.getId());
                boolean joined=room.members().stream().anyMatch(m->m.userId()==user.getId());
                send(socket,new RoomEvent(1,"EVENT",joined?"ROOM_UPDATED":"ROOM_ACCESS_REVOKED",RoomTarget.of(room.id()),eventId,
                        room.revision(),now,joined?room:Map.of("reason","NOT_A_MEMBER")));
                if(!joined || room.status()==RoomStatus.CLOSED) binding.rooms().remove(room.id());
            } catch(AuthFailure e) {
                remove(binding.sessionId(),socket);
                try { socket.close(new CloseStatus(4001,"SESSION_EXPIRED")); } catch(IOException ignored) {}
            } catch(org.springframework.dao.DataAccessException e) {
                remove(binding.sessionId(),socket);
                try { socket.close(new CloseStatus(1011,"AUTH_UNAVAILABLE")); } catch(IOException ignored) {}
            }
        }
    }
    @EventListener
    public void gameChanged(vn.edu.multigame.game.service.GameLifecycleEvent changed) {
        var game=changed.snapshot(); String eventId=UUID.randomUUID().toString();
        for(Binding binding:bindings.values()) {
            if(!binding.rooms().contains(game.roomId())) continue;
            var socket=binding.outbound();
            try {
                var user=users.requireActiveUser(socket.getPrincipal() instanceof Authentication auth?auth:null);
                sessions.requireValid(binding.sessionId(),user.getId());
                if(game.members().stream().noneMatch(m -> m.userId()==user.getId())) continue;
                send(socket,new vn.edu.multigame.realtime.message.event.GameEvent(1,"EVENT",changed.type(),
                        vn.edu.multigame.realtime.message.common.GameTarget.of(game.gameSessionId()),game.questionIndex()>0?game.questionIndex():null,
                        eventId,game.revision(),game.serverTimeMs(),game.forPlayer(changed.players().get(user.getId()))));
            } catch(AuthFailure failure) {
                remove(binding.sessionId(),socket);
                try { socket.close(new CloseStatus(4001,"SESSION_EXPIRED")); } catch(IOException ignored) {}
            } catch(org.springframework.dao.DataAccessException failure) {
                remove(binding.sessionId(),socket);
                try { socket.close(new CloseStatus(1011,"AUTH_UNAVAILABLE")); } catch(IOException ignored) {}
            }
        }
        if(changed.terminalRoom()!=null && changed.type().equals("GAME_END")) roomChanged(new RoomChanged(changed.terminalRoom()));
    }
}
