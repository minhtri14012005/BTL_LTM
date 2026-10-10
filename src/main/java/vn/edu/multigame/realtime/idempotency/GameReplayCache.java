package vn.edu.multigame.realtime.idempotency;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import vn.edu.multigame.game.service.GameFailure;
import vn.edu.multigame.realtime.message.common.GameAck;

/** Owned by one serial GameSession. No eviction before session retention expires. */
public final class GameReplayCache {
    public static final int USER_LIMIT=192, SESSION_LIMIT=8192, SERVER_LIMIT=65536;
    private record Key(long userId,String requestId) {}
    private record Cached(String fingerprint,GameAck response) {}
    private final Map<Key,Cached> receipts=new HashMap<>();
    private final Map<Long,Integer> counts=new HashMap<>();
    private final Semaphore budget;
    private boolean closed;
    public GameReplayCache(Semaphore budget) { this.budget=budget; }
    public synchronized GameAck lookup(long userId,String requestId,String fingerprint) {
        if(closed) throw GameFailure.unavailable();
        var cached=receipts.get(new Key(userId,requestId));
        if(cached==null) return null;
        if(!cached.fingerprint().equals(fingerprint)) throw GameFailure.conflict("INVALID_REQUEST_ID");
        return cached.response();
    }
    public synchronized Slot reserve(long userId,String requestId,String fingerprint) {
        if(closed) throw GameFailure.unavailable();
        if(receipts.size()>=SESSION_LIMIT || counts.getOrDefault(userId,0)>=USER_LIMIT || !budget.tryAcquire())
            throw new GameFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"RECEIPT_LIMIT_REACHED");
        return new Slot(new Key(userId,requestId),fingerprint);
    }
    public final class Slot implements AutoCloseable {
        private final Key key;
        private final String fingerprint;
        private boolean done;
        private Slot(Key key,String fingerprint) { this.key=key; this.fingerprint=fingerprint; }
        public void commit(GameAck response) {
            synchronized(GameReplayCache.this) {
                if(done) throw new IllegalStateException("Receipt slot already completed");
                if(closed) { close(); throw GameFailure.unavailable(); }
                receipts.put(key,new Cached(fingerprint,response)); counts.merge(key.userId(),1,Integer::sum); done=true;
            }
        }
        @Override public void close() { synchronized(GameReplayCache.this) { if(!done) { done=true; budget.release(); } } }
    }
    /** Called only after the queue is idle and removed, or after queue shutdown. */
    public synchronized void clear() { closed=true; budget.release(receipts.size()); receipts.clear(); counts.clear(); }
}
