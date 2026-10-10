package vn.edu.multigame.realtime.connection;

import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import jakarta.annotation.PreDestroy;
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
import vn.edu.multigame.room.service.RoomChanged;
import vn.edu.multigame.room.enums.RoomStatus;
import vn.edu.multigame.realtime.message.common.RoomTarget;
import vn.edu.multigame.realtime.message.event.RoomEvent;

@Component
@Profile("mysql")
public class AuthenticatedSocketRegistry {
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sockets = new ConcurrentHashMap<>();
    private record Binding(String sessionId,long userId,long generation,WebSocketSession outbound,SocketOutbound sender,Set<Long> rooms) {}
    private final Map<String,Binding> bindings=new ConcurrentHashMap<>();
    private final Map<Long,Binding> active=new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong generations=new java.util.concurrent.atomic.AtomicLong();
    private final ObjectMapper json;
    private final AuthSessionRegistry sessions;
    private final AuthService users;
    private final ExecutorService writers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("socket-writer-",0).factory());
    private final ScheduledThreadPoolExecutor watchdog=new ScheduledThreadPoolExecutor(1,runnable->{
        var thread=new Thread(runnable,"socket-send-watchdog");thread.setDaemon(true);return thread;
    });
    public record PresenceChange(long userId,boolean connected) {}
    private final Object presenceLock=new Object();
    private final Map<Long,Long> connectedGenerations=new java.util.HashMap<>();
    private final Set<java.util.function.Consumer<PresenceChange>> presenceListeners=new java.util.HashSet<>();
    public void onPresence(java.util.function.Consumer<PresenceChange> listener) {
        synchronized(presenceLock) {presenceListeners.add(listener);}
    }
    public void removePresenceListener(java.util.function.Consumer<PresenceChange> listener) {
        synchronized(presenceLock) {presenceListeners.remove(listener);}
    }
    /** Bootstrap and installation are atomic with transition admission into each Game queue. */
    public void withPresence(java.util.function.Consumer<Set<Long>> install) {
        synchronized(presenceLock) {install.accept(onlineUsers());}
    }
    private void presence(long userId,boolean connected) {
        presenceListeners.forEach(listener -> listener.accept(new PresenceChange(userId,connected)));
    }
    public AuthenticatedSocketRegistry(ObjectMapper json,AuthSessionRegistry sessions,AuthService users) {
        this.json=json; this.sessions=sessions; this.users=users;
        watchdog.setRemoveOnCancelPolicy(true);
        watchdog.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }
    public long add(String sessionId, WebSocketSession socket,long userId) {
        // The transport timeout supplements the independent outbound watchdog.
        socket.getAttributes().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT",5000L);
        var replaced=new java.util.concurrent.atomic.AtomicReference<Binding>();
        Binding binding;
        synchronized(presenceLock) {
            binding=active.compute(userId,(uid,previous) -> {
                // SocketOutbound owns serialization, buffering and timeouts. A second
                // decorator would overwrite our explicit timeout close status.
                var outbound=socket;
                var sender=new SocketOutbound(outbound,writers,watchdog,status->remove(sessionId,socket));
                var created=new Binding(sessionId,userId,generations.incrementAndGet(),outbound,sender,ConcurrentHashMap.newKeySet());
                bindings.put(socket.getId(),created);
                sockets.compute(sessionId,(id,existing) -> {
                    Set<WebSocketSession> set=existing==null?ConcurrentHashMap.newKeySet():existing;
                    set.add(socket); return set;
                });
                replaced.set(previous); return created;
            });
        }
        var previous=replaced.get();
        if(previous!=null) {
            // Notification bypasses the normal current-generation send guard.
            detach(previous.sessionId(),previous.outbound());
            try { previous.sender().finish(new TextMessage(json.writeValueAsString(
                    vn.edu.multigame.realtime.message.event.SessionReplaced.of(previous.generation(),binding.generation()))),new CloseStatus(4002,"SESSION_REPLACED")); }
            catch(IOException | RuntimeException failure) { previous.sender().abort(new CloseStatus(1011,"SEND_UNAVAILABLE")); }
        }
        return binding.generation();
    }
    /** A connection ends its offline episode only after the post-registration auth/session check. */
    public void connected(WebSocketSession socket) {
        synchronized(presenceLock) {
            long generation=requireCurrent(socket);var binding=bindings.get(socket.getId());
            var previous=connectedGenerations.put(binding.userId(),generation);
            if(previous==null || previous!=generation) presence(binding.userId(),true);
        }
    }
    /** Bootstrap/diagnostics only; ongoing gameplay consumes ordered presence transitions. */
    public Set<Long> onlineUsers() {
        synchronized(presenceLock) {
            return connectedGenerations.keySet().stream().filter(user -> active.containsKey(user) && active.get(user).outbound().isOpen())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
    /** Linearization point for permission to begin an operation; running transactions may finish. */
    public long requireCurrent(WebSocketSession socket) {
        var binding=bindings.get(socket.getId());
        if(binding==null || active.get(binding.userId())!=binding) throw new SocketReplaced();
        return binding.generation();
    }
    public void remove(String sessionId, WebSocketSession socket) {
        var binding=detach(sessionId,socket);if(binding!=null)binding.sender().stop();
    }
    private Binding detach(String sessionId,WebSocketSession socket) {
        synchronized(presenceLock) {
            var binding=bindings.remove(socket.getId());
            if(binding!=null && active.remove(binding.userId(),binding) && connectedGenerations.remove(binding.userId())!=null) presence(binding.userId(),false);
            sockets.computeIfPresent(sessionId, (id, existing) -> { existing.removeIf(s->s.getId().equals(socket.getId())); return existing.isEmpty() ? null : existing; });
            return binding;
        }
    }
    /** Detach immediately, perform potentially blocking close outside the gameplay worker. */
    public java.util.concurrent.CompletableFuture<Void> close(WebSocketSession socket,CloseStatus status) {
        var binding=bindings.get(socket.getId());
        if(binding!=null)return binding.sender().abort(status);
        try {return java.util.concurrent.CompletableFuture.runAsync(()->SocketOutbound.close(socket,status),writers);}
        catch(RuntimeException shuttingDown){return java.util.concurrent.CompletableFuture.completedFuture(null);}
    }
    @EventListener
    public void revoked(AuthSessionRevoked event) {
        Set<WebSocketSession> set = sockets.remove(event.sessionId());
        if (set == null) return;
        for (WebSocketSession socket : set) {
            event.deliveredAfter(close(socket,new CloseStatus(4001,event.reason())));
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
        try { binding.sender().send(new TextMessage(json.writeValueAsString(message))); }
        catch(IOException | RuntimeException failure) {
            binding.sender().abort(new CloseStatus(1011,"SEND_UNAVAILABLE"));
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
                close(socket,new CloseStatus(4001,"SESSION_EXPIRED"));
            } catch(org.springframework.dao.DataAccessException e) {
                close(socket,new CloseStatus(1011,"AUTH_UNAVAILABLE"));
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
                close(socket,new CloseStatus(4001,"SESSION_EXPIRED"));
            } catch(org.springframework.dao.DataAccessException failure) {
                close(socket,new CloseStatus(1011,"AUTH_UNAVAILABLE"));
            }
        }
        if(changed.terminalRoom()!=null && changed.type().equals("GAME_END")) roomChanged(new RoomChanged(changed.terminalRoom()));
    }
    @PreDestroy public void shutdown() {
        bindings.values().forEach(binding->close(binding.outbound(),CloseStatus.GOING_AWAY));
        watchdog.shutdownNow();writers.shutdown();
    }
}
