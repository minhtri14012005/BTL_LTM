package vn.edu.multigame.realtime.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.multigame.room.dto.response.RoomResponse;
import vn.edu.multigame.room.enums.RoomStatus;
import vn.edu.multigame.room.service.*;

/** Single-server bounded fair admission queue. No transaction may surround this boundary. */
@Component @Profile("mysql")
public class RoomBoundary {
    public record Scope(String kind,long id) {
        public static Scope room(long id) { return new Scope("ROOM",id); }
        public static Scope create(long userId) { return new Scope("USER_CREATE_ROOM",userId); }
    }
    public record Receipt(RoomResponse snapshot, long serverTimeMs, Long gameSessionId) {
        public Receipt(RoomResponse snapshot, long serverTimeMs) { this(snapshot, serverTimeMs, null); }
    }
    private record Key(long userId,String requestId) {}
    private record Cached(String fingerprint,Receipt receipt,long expiresAtMs) {}
    private static final long RETENTION_MS=600_000;
    private static final int MAX_SCOPES=512, MAX_RECEIPTS=1024, ADMISSION=65;
    private static class State {
        final ReentrantLock lock=new ReentrantLock(true);
        final Semaphore admission=new Semaphore(ADMISSION);
        final Map<Key,Cached> receipts=new HashMap<>();
        volatile long closedAtMs;
        boolean unavailable;
    }
    private final Map<Scope,State> states=new HashMap<>();
    private final AtomicInteger receiptCount=new AtomicInteger();
    private final ApplicationEventPublisher events;
    public RoomBoundary(ApplicationEventPublisher events) { this.events=events; }
    public <T> T read(Scope scope,Supplier<T> operation) {
        return serialized(scope,s->{
            T result=operation.get();
            if(result instanceof RoomResponse room && room.status()==RoomStatus.CLOSED && s.closedAtMs==0) s.closedAtMs=System.currentTimeMillis();
            return result;
        });
    }
    public Receipt mutate(Scope scope,long userId,String requestId,String fingerprint,Runnable authorize,
            Supplier<RoomMutation> committedOperation, BiConsumer<Receipt,Boolean> afterCommit) {
        return serialized(scope,state->{
            authorize.run(); // Identity/target rights before replay, state/revision checks in operation afterwards.
            Key key=new Key(userId,requestId); Cached cached=state.receipts.get(key);
            if(cached!=null && scope.kind().equals("USER_CREATE_ROOM") && cached.expiresAtMs()<System.currentTimeMillis()) {
                state.receipts.remove(key); receiptCount.decrementAndGet(); cached=null;
            }
            if(cached!=null) {
                if(!cached.fingerprint().equals(fingerprint)) throw RoomFailure.conflict("INVALID_REQUEST_ID");
                afterCommit.accept(cached.receipt(),true); return cached.receipt();
            }
            if(state.unavailable) throw new RoomFailure(HttpStatus.SERVICE_UNAVAILABLE,"ROOM_UNAVAILABLE");
            if(receiptCount.incrementAndGet()>MAX_RECEIPTS) { receiptCount.decrementAndGet(); throw busy("RECEIPT_LIMIT_REACHED"); }
            boolean stored=false;
            try {
                RoomMutation result;
                try { result=committedOperation.get(); }
                catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
                    // One attempt. Commit outcome may be ambiguous: never blindly retry a mutation.
                    state.unavailable=true; throw new RoomFailure(HttpStatus.SERVICE_UNAVAILABLE,"ROOM_UNAVAILABLE");
                }
                if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Operation did not commit before returning");
                Receipt receipt=new Receipt(result.snapshot(),System.currentTimeMillis(),result.gameSessionId());
                state.receipts.put(key,new Cached(fingerprint,receipt,receipt.serverTimeMs()+RETENTION_MS)); stored=true;
                if(result.snapshot().status()==RoomStatus.CLOSED && state.closedAtMs==0) state.closedAtMs=receipt.serverTimeMs();
                // Receipt is retained before delivery; network failure must never repeat persistence.
                try { afterCommit.accept(receipt,false); }
                finally { if(result.changed()) events.publishEvent(new RoomChanged(receipt.snapshot())); }
                return receipt;
            } finally { if(!stored) receiptCount.decrementAndGet(); }
        });
    }
    private <T> T serialized(Scope scope,Function<State,T> operation) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Enter Room boundary before transaction");
        State state;
        synchronized(states) {
            state=states.get(scope);
            if(state==null) { if(states.size()>=MAX_SCOPES) throw busy("SERVER_BUSY"); state=new State(); states.put(scope,state); }
            if(!state.admission.tryAcquire()) throw busy("SERVER_BUSY");
        }
        boolean locked=false;
        try {
            locked=state.lock.tryLock(5,TimeUnit.SECONDS);
            if(!locked) throw busy("SERVER_BUSY");
            return operation.apply(state);
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw busy("SERVER_BUSY"); }
        finally { if(locked) state.lock.unlock(); state.admission.release(); }
    }
    @Scheduled(fixedDelay=60_000)
    public void cleanup() {
        long now=System.currentTimeMillis();
        synchronized(states) {
            var iterator=states.entrySet().iterator();
            while(iterator.hasNext()) {
                var entry=iterator.next(); State state=entry.getValue();
                if(state.admission.availablePermits()!=ADMISSION || !state.lock.tryLock()) continue;
                try {
                    if(entry.getKey().kind().equals("USER_CREATE_ROOM")) {
                        int previous=state.receipts.size(); state.receipts.values().removeIf(c->c.expiresAtMs()<now);
                        receiptCount.addAndGet(state.receipts.size()-previous);
                        if(state.receipts.isEmpty() && !state.unavailable) iterator.remove();
                    } else if(state.closedAtMs>0 && now-state.closedAtMs>RETENTION_MS) {
                        receiptCount.addAndGet(-state.receipts.size()); iterator.remove();
                    }
                } finally { state.lock.unlock(); }
            }
        }
    }
    private RoomFailure busy(String code) { return new RoomFailure(HttpStatus.SERVICE_UNAVAILABLE,code); }
}
