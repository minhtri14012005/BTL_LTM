package vn.edu.multigame.realtime.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;
import vn.edu.multigame.room.dto.response.RoomResponse;
import vn.edu.multigame.room.enums.RoomStatus;
import vn.edu.multigame.room.service.*;
import static org.assertj.core.api.Assertions.*;

/** Pure runtime policy/ordering tests; not a MySQL or lifecycle substitute. */
class RoomBoundaryTest {
    RoomResponse snapshot(long revision) { return new RoomResponse(1,2,3,"Quiz",false,"ABCDEFGH2345","Room",RoomStatus.WAITING,3,10000,5000,1,revision,List.of()); }
    @Test void concurrentDuplicateAndOtherRequestRunOnlyAfterFirstCommitDelivery() throws Exception {
        List<Object> events=new CopyOnWriteArrayList<>(); var boundary=new RoomBoundary(events::add);
        CountDownLatch firstStarted=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger(); List<String> trace=new CopyOnWriteArrayList<>();
        try(var threads=Executors.newFixedThreadPool(3)) {
            var first=threads.submit(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"a","fp",()->{},()->{
                calls.incrementAndGet(); trace.add("first"); firstStarted.countDown();
                try { if(!release.await(3,TimeUnit.SECONDS)) throw new AssertionError("test barrier timed out"); }
                catch(InterruptedException e) { throw new AssertionError(e); }
                return new RoomMutation(snapshot(1),true);
            },(r,replay)->trace.add("delivery")));
            assertThat(firstStarted.await(2,TimeUnit.SECONDS)).isTrue();
            var duplicate=threads.submit(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"a","fp",()->{},()->{
                calls.incrementAndGet(); throw new AssertionError("duplicate executed");
            },(r,replay)->assertThat(replay).isTrue()));
            var other=threads.submit(()->boundary.read(RoomBoundary.Scope.room(1),()->{trace.add("read");return 1;}));
            release.countDown(); assertThat(duplicate.get(3,TimeUnit.SECONDS)).isEqualTo(first.get(3,TimeUnit.SECONDS)); other.get(3,TimeUnit.SECONDS);
            assertThat(calls).hasValue(1); assertThat(trace).containsExactly("first","delivery","read"); assertThat(events).hasSize(1);
        }
    }
    @Test void fingerprintIncludesTypeTargetIndexPayloadAndCanonicalObjectOrder() {
        var fp=new CommandFingerprint(new ObjectMapper());
        String first=fp.of("JOIN_ROOM","ROOM",1,null,Map.of("b",2,"a",List.of("x","y")));
        var ordered=new LinkedHashMap<String,Object>(); ordered.put("a",List.of("x","y")); ordered.put("b",2);
        assertThat(fp.of("JOIN_ROOM","ROOM",1,null,ordered)).isEqualTo(first);
        assertThat(fp.of("LEAVE_ROOM","ROOM",1,null,ordered)).isNotEqualTo(first);
        assertThat(fp.of("JOIN_ROOM","ROOM",2,null,ordered)).isNotEqualTo(first);
        assertThat(fp.of("JOIN_ROOM","ROOM",1,1,ordered)).isNotEqualTo(first);
        assertThat(fp.of("JOIN_ROOM","ROOM",1,null,Map.of("b",2,"a",List.of("y","x")))).isNotEqualTo(first);
    }
    @Test void authBeforeReplayConflictAndCommittedSendFailureNeverRepeatsOperation() {
        AtomicInteger calls=new AtomicInteger(),published=new AtomicInteger(); var boundary=new RoomBoundary(e->published.incrementAndGet());
        var operation=(java.util.function.Supplier<RoomMutation>)()->{calls.incrementAndGet();return new RoomMutation(snapshot(1),true);};
        assertThatThrownBy(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"a","fp",()->{},operation,(r,replay)->{throw new IllegalStateException("send");})).isInstanceOf(IllegalStateException.class);
        var replay=boundary.mutate(RoomBoundary.Scope.room(1),2,"a","fp",()->{},operation,(r,b)->assertThat(b).isTrue());
        assertThat(replay.snapshot().revision()).isEqualTo(1); assertThat(calls).hasValue(1); assertThat(published).hasValue(1);
        assertThatThrownBy(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"a","changed",()->{},operation,(r,b)->{}))
                .isInstanceOf(RoomFailure.class).hasMessage("INVALID_REQUEST_ID");
        assertThatThrownBy(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"a","fp",()->{throw RoomFailure.forbidden();},operation,(r,b)->{}))
                .isInstanceOf(RoomFailure.class).hasMessage("FORBIDDEN");
        boundary.mutate(RoomBoundary.Scope.room(2),2,"a","changed",()->{},operation,(r,b)->{}); assertThat(calls).hasValue(2);
    }
    @Test void databaseFailureHasNoReceiptOrEventAndFencesFutureMutations() {
        AtomicInteger events=new AtomicInteger(); var boundary=new RoomBoundary(e->events.incrementAndGet());
        assertThatThrownBy(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"a","fp",()->{},()->{
            throw new org.springframework.dao.DataAccessResourceFailureException("database");
        },(r,b)->{throw new AssertionError("must not ACK");})).hasMessage("ROOM_UNAVAILABLE");
        assertThatThrownBy(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"a","fp",()->{},()->{
            throw new AssertionError("must not retry");
        },(r,b)->{})).hasMessage("ROOM_UNAVAILABLE"); assertThat(events).hasValue(0);
        assertThat(boundary.read(RoomBoundary.Scope.room(1),()->"read-only snapshot")).isEqualTo("read-only snapshot");
    }
    @Test void finiteGlobalCapacityStillReplaysCommittedReceipt() {
        var boundary=new RoomBoundary(e->{});
        for(int i=0;i<1024;i++) boundary.mutate(RoomBoundary.Scope.room(1),2,"r"+i,"fp",()->{},()->new RoomMutation(snapshot(1),false),(r,b)->{});
        assertThatThrownBy(()->boundary.mutate(RoomBoundary.Scope.room(1),2,"overflow","fp",()->{},()->{throw new AssertionError("side effect");},(r,b)->{})).hasMessage("RECEIPT_LIMIT_REACHED");
        assertThat(boundary.mutate(RoomBoundary.Scope.room(1),2,"r0","fp",()->{},()->{throw new AssertionError("replay");},(r,b)->{}).snapshot().revision()).isEqualTo(1);
    }
}
