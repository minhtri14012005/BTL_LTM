package vn.edu.multigame.realtime.idempotency;

import java.util.*;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.realtime.message.common.*;
import static org.assertj.core.api.Assertions.*;

class GameReplayCacheTest {
    GameAck ack(String id) { return new GameAck(1,"ACK",id,"USE_STAR",GameTarget.of(1),1,"ACCEPTED",5,10,
            new GameAck.Payload(null,false,null,true,1,false,List.of())); }
    @Test void rollbackReleasesCapacityWithoutCachingAndReplayReturnsOriginalObject() {
        var budget=new Semaphore(1); var cache=new GameReplayCache(budget);
        try(var abandoned=cache.reserve(3,"req","fp")) { assertThat(cache.lookup(3,"req","fp")).isNull(); }
        assertThat(budget.availablePermits()).isEqualTo(1);
        var original=ack("req"); try(var committed=cache.reserve(3,"req","fp")) { committed.commit(original); }
        assertThat(cache.lookup(3,"req","fp")).isSameAs(original);
        assertThatThrownBy(() -> cache.lookup(3,"req","different")).hasMessage("INVALID_REQUEST_ID");
        assertThat(cache.lookup(4,"req","fp")).isNull();
        assertThatThrownBy(() -> cache.reserve(4,"new","fp")).hasMessage("RECEIPT_LIMIT_REACHED");
        cache.clear(); cache.clear(); assertThat(budget.availablePermits()).isEqualTo(1);
    }
    @Test void userLimitRejectsBeforeEffectsButPreservesReplay() {
        var cache=new GameReplayCache(new Semaphore(200));
        for(int i=0;i<GameReplayCache.USER_LIMIT;i++) try(var slot=cache.reserve(3,"r"+i,"fp")) { slot.commit(ack("r"+i)); }
        assertThatThrownBy(() -> cache.reserve(3,"overflow","fp")).hasMessage("RECEIPT_LIMIT_REACHED");
        assertThat(cache.lookup(3,"r0","fp")).isNotNull();
        try(var differentUser=cache.reserve(4,"r0","fp")) { differentUser.commit(ack("r0")); }
    }
    @Test void shutdownDoesNotResurrectClearedReceiptsOrLeakPendingCapacity() {
        var budget=new Semaphore(1); var cache=new GameReplayCache(budget); var slot=cache.reserve(3,"req","fp");
        cache.clear(); assertThatThrownBy(() -> slot.commit(ack("req"))).hasMessage("SERVICE_UNAVAILABLE");
        slot.close(); assertThat(budget.availablePermits()).isEqualTo(1);
    }
    @Test void fingerprintUsesTypeTargetIndexCanonicalPayloadAndArrayOrder() {
        var fingerprint=new CommandFingerprint(new ObjectMapper());
        var first=new LinkedHashMap<String,Object>(); first.put("a",1);first.put("b",List.of(2,3));
        var reordered=new LinkedHashMap<String,Object>(); reordered.put("b",List.of(2,3));reordered.put("a",1);
        String original=fingerprint.of("ANSWER","GAME",1,1,first);
        assertThat(fingerprint.of("ANSWER","GAME",1,1,reordered)).isEqualTo(original);
        assertThat(fingerprint.of("USE_SPIN","GAME",1,1,first)).isNotEqualTo(original);
        assertThat(fingerprint.of("ANSWER","GAME",2,1,first)).isNotEqualTo(original);
        assertThat(fingerprint.of("ANSWER","ROOM",1,1,first)).isNotEqualTo(original);
        assertThat(fingerprint.of("ANSWER","GAME",1,2,first)).isNotEqualTo(original);
        assertThat(fingerprint.of("ANSWER","GAME",1,1,Map.of("a",1,"b",List.of(3,2)))).isNotEqualTo(original);
    }
}
