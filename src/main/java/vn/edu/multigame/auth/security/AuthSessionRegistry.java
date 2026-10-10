package vn.edu.multigame.auth.security;

import jakarta.servlet.http.HttpSession;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.multigame.auth.service.AuthService;

/** WS frames do not refresh HTTP idle timeout. Only authenticated sessions are tracked. */
@Component
@Profile("mysql")
public class AuthSessionRegistry {
    private record Binding(HttpSession session, long userId) {}
    private final ConcurrentHashMap<String, Binding> sessions = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;
    private final int timeoutSeconds;
    public AuthSessionRegistry(ApplicationEventPublisher events, ServerProperties properties) {
        this.events = events;
        long seconds = properties.getServlet().getSession().getTimeout().toSeconds();
        if (seconds < 1 || seconds > Integer.MAX_VALUE) throw new IllegalArgumentException("Session timeout must be finite and positive");
        timeoutSeconds = (int) seconds;
    }
    public void register(HttpSession session, long userId) {
        session.setMaxInactiveInterval(timeoutSeconds);
        sessions.put(session.getId(), new Binding(session, userId));
    }
    public void requireValid(String id, long userId) {
        Binding binding = id == null ? null : sessions.get(id);
        if (binding == null || binding.userId() != userId) throw AuthService.unauthorized();
        if (expired(binding.session())) {
            revoke(id, "SESSION_EXPIRED"); throw AuthService.unauthorized();
        }
    }
    /** Capture the original login; validate again when a queued REST operation actually begins. */
    public Runnable validation(HttpSession session,long userId) {
        String id=session==null?null:session.getId();
        return () -> requireValid(id,userId);
    }
    private boolean expired(HttpSession session) {
        try {
            return System.currentTimeMillis() - session.getLastAccessedTime()
                    >= session.getMaxInactiveInterval() * 1000L;
        } catch (IllegalStateException e) { return true; }
    }
    public void revoke(String id, String reason) {
        Binding removed = sessions.remove(id);
        if (removed == null) return;
        // Remove HTTP authentication now as well; delayed physical invalidation must
        // not leave the revoked cookie authorized on other REST endpoints.
        try {removed.session().removeAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);}
        catch(IllegalStateException alreadyInvalid){/* Container expiry. */}
        var revoked=new AuthSessionRevoked(id,reason);
        events.publishEvent(revoked);
        // Authority is already revoked. Let transport close with 4001 before Tomcat's
        // HTTP-session invalidation emits its own 1008 close; never wait on the caller.
        revoked.delivered().completeOnTimeout(null,5,java.util.concurrent.TimeUnit.SECONDS).whenComplete((ignored,failure)->{
            try {
                // Login may rotate and reuse this HTTP session while close is pending.
                if(id.equals(removed.session().getId()))removed.session().invalidate();
            }catch(IllegalStateException alreadyInvalid){/* Container expiry. */}
        });
    }
    @Scheduled(fixedDelay = 1000)
    public void expireIdleSessions() {
        sessions.forEach((id, binding) -> { if (expired(binding.session())) revoke(id, "SESSION_EXPIRED"); });
    }
}
