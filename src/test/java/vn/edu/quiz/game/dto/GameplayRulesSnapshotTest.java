package vn.edu.quiz.game.dto;

import vn.edu.quiz.game.enums.SpinEffect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

/** Expected values transcribed from Overview §§8.2–8.7, independent of the factory. */
class GameplayRulesSnapshotTest {
    @ParameterizedTest
    @CsvSource({
        "BONUS,25,15,-4,-1,30,-4,-1",
        "SAFE,20,8,-2,0,20,-2,0",
        "BREAKTHROUGH,25,18,-7,-3,30,-12,-6",
        "SPEED,15,22,-10,-4,35,-16,-8",
        "DECISIVE,10,28,-15,-7,40,-22,-10",
        "HARDSHIP,5,8,-6,-2,0,0,0"
    })
    void preservesEverySpinScoreAndWeight(SpinEffect effect, int weight, int correct, int wrong,
            int noAnswer, int starCorrect, int starWrong, int starNoAnswer) {
        var rule = GameplayRulesSnapshot.forGame(10, 30000, 5000).spins().get(effect);
        assertThat(rule.weight()).isEqualTo(weight);
        assertThat(rule.normal()).isEqualTo(new GameplayRulesSnapshot.Score(correct, wrong, noAnswer));
        if (effect == SpinEffect.HARDSHIP) {
            assertThat(rule.starAllowed()).isFalse();
            assertThat(rule.withStar()).isNull();
        } else {
            assertThat(rule.starAllowed()).isTrue();
            assertThat(rule.withStar()).isEqualTo(new GameplayRulesSnapshot.Score(starCorrect, starWrong, starNoAnswer));
        }
    }

    @Test void preservesNormalStarStreakRecoveryAndUserResolvedEndRules() {
        var s = GameplayRulesSnapshot.forGame(50, 30000, 5000);
        assertThat(s.initialScore()).isEqualTo(20);
        assertThat(s.spinCredits()).isEqualTo(5);
        assertThat(s.starCredits()).isEqualTo(1);
        assertThat(s.normal()).isEqualTo(new GameplayRulesSnapshot.Score(10, -4, -1));
        assertThat(s.starOnly()).isEqualTo(new GameplayRulesSnapshot.Score(25, -14, -7));
        assertThat(s.streaks()).isEqualTo(new GameplayRulesSnapshot.StreakRules(5, 3, 3, 0, true, true, false, false, true, true));
        assertThat(s.eliminationRule()).isEqualTo("SCORE_LT_ZERO");
        assertThat(s.endReasonPriority()).isEqualTo("ALL_ELIMINATED_OR_ONE_SURVIVOR_BEFORE_COMPLETED");
        assertThat(s.allEliminatedRankOneAreWinners()).isTrue();
        assertThat(s.spins().values().stream().mapToInt(GameplayRulesSnapshot.SpinRule::weight).sum()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({"10,1","19,1","20,2","29,2","30,3","39,3","40,4","49,4","50,5"})
    void snapshotsSpinCreditBoundaries(int count, int credits) {
        assertThat(GameplayRulesSnapshot.forGame(count, 1, 5000).spinCredits()).isEqualTo(credits);
    }
}
