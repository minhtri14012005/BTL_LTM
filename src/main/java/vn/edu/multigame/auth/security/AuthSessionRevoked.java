package vn.edu.multigame.auth.security;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Transport independent notification with nonblocking delivery completion. */
public final class AuthSessionRevoked {
    private final String sessionId,reason;
    private final List<CompletableFuture<Void>> deliveries=new ArrayList<>();
    public AuthSessionRevoked(String sessionId,String reason){this.sessionId=sessionId;this.reason=reason;}
    public String sessionId(){return sessionId;}
    public String reason(){return reason;}
    /** Synchronous event listeners register asynchronous delivery before publishEvent returns. */
    public void deliveredAfter(CompletableFuture<Void> delivery){deliveries.add(delivery);}
    public CompletableFuture<Void> delivered(){return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new));}
}
