package vn.edu.multigame.questionbank.model;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ClueTimelineTest {
    Map<String,Object> timeline(long... offsets) {
        return Map.of("hints",Arrays.stream(offsets).mapToObj(n -> Map.of("offsetMs",n,"text","Gợi ý")).toList());
    }
    @Test void offsetsAreOrderedImmutableAndOptionalZeroIsSupported() {
        assertThat(ClueTimeline.offsets(timeline(0,100,999))).containsExactly(0L,100L,999L);
        assertThat(ClueTimeline.offsets(timeline(100))).containsExactly(100L);
        assertThatThrownBy(() -> ClueTimeline.offsets(timeline(100)).add(200L)).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void everyOffsetMustBeStrictlyBeforeTheQuestionDeadline() {
        assertThat(ClueTimeline.fits(timeline(0,999),1000)).isTrue();
        assertThat(ClueTimeline.fits(timeline(0,1000),1000)).isFalse();
        assertThat(ClueTimeline.fits(timeline(0,1001),1000)).isFalse();
    }
    @Test void malformedEmptyDuplicateDescendingAndNegativeTimelinesAreRejected() {
        for(var p:List.of(timeline(),timeline(-1),timeline(0,0),timeline(100,0),
                Map.<String,Object>of("hints",List.of(Map.of("offsetMs",1.5,"text","x"))),
                Map.<String,Object>of("hints",List.of(Map.of("offsetMs",0,"text"," ")))))
            assertThat(ClueTimeline.fits(p,1000)).isFalse();
    }
}
