package vn.edu.multigame.game.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.game.dto.GameplayRulesSnapshot;
import vn.edu.multigame.game.enums.SpinEffect;
import static org.assertj.core.api.Assertions.*;

class SpinSelectorTest {
    final GameplayRulesSnapshot rules=GameplayRulesSnapshot.forGame(20,1000,5000);
    @Test void everyWeightedBoundaryMatchesOverview() {
        var pool=List.of(SpinEffect.values());
        // Overview cumulative ranges: 0..24 BONUS,25..44 SAFE,45..69 BREAKTHROUGH,
        // 70..84 SPEED,85..94 DECISIVE,95..99 HARDSHIP.
        int[] draws={0,24,25,44,45,69,70,84,85,94,95,99};
        var expected=List.of(SpinEffect.BONUS,SpinEffect.BONUS,SpinEffect.SAFE,SpinEffect.SAFE,
                SpinEffect.BREAKTHROUGH,SpinEffect.BREAKTHROUGH,SpinEffect.SPEED,SpinEffect.SPEED,
                SpinEffect.DECISIVE,SpinEffect.DECISIVE,SpinEffect.HARDSHIP,SpinEffect.HARDSHIP);
        for(int i=0;i<draws.length;i++) { int draw=draws[i]; assertThat(new SpinSelector(bound -> { assertThat(bound).isEqualTo(100); return draw; }).select(rules,pool)).isEqualTo(expected.get(i)); }
    }
    @Test void removingBonusNormalizesRemainingWeightInsteadOfEqualProbability() {
        var pool=new ArrayList<>(List.of(SpinEffect.values())); pool.remove(SpinEffect.BONUS);
        assertThat(new SpinSelector(bound -> { assertThat(bound).isEqualTo(75); return 19; }).select(rules,pool)).isEqualTo(SpinEffect.SAFE);
        assertThat(new SpinSelector(bound -> 20).select(rules,pool)).isEqualTo(SpinEffect.BREAKTHROUGH);
        assertThat(pool).hasSize(5); // Selector does not mutate/persist the pool.
    }
    @Test void emptyPoolAndInvalidSamplerCannotProduceAnEffect() {
        assertThatThrownBy(() -> new SpinSelector(bound -> 0).select(rules,List.of())).hasMessage("SPIN_NOT_AVAILABLE");
        assertThatThrownBy(() -> new SpinSelector(bound -> bound).select(rules,List.of(SpinEffect.HARDSHIP))).hasMessage("Invalid server draw");
    }
}
