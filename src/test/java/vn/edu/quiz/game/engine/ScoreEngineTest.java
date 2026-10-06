package vn.edu.quiz.game.engine;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.game.enums.*;
import static org.assertj.core.api.Assertions.*;

/** Golden deltas transcribed from Overview §§8.2/8.6/8.7; effect results worked out from §§8.4/8.5. */
class ScoreEngineTest {
    final ScoreEngine engine = new ScoreEngine(GameplayRulesSnapshot.forGame(10, 40000, 5000));
    record Table(SpinEffect spin, boolean star, int correct, int wrong, int noAnswer,
            int momentumCorrect, int recoveryWrong, int recoveryNoAnswer, boolean noAnswerConsumesRecovery) {}

    static Stream<Table> tables() {
        // No calls to production table/engine to create expected values, and no copied modifier algorithm.
        return Stream.of(
            new Table(null, false, 10, -4, -1, 13, -1, 0, true),
            new Table(null, true, 25, -14, -7, 28, -11, -4, true),
            new Table(SpinEffect.BONUS, false, 15, -4, -1, 18, -1, 0, true),
            new Table(SpinEffect.SAFE, false, 8, -2, 0, 11, 0, 0, false),
            new Table(SpinEffect.BREAKTHROUGH, false, 18, -7, -3, 21, -4, 0, true),
            new Table(SpinEffect.SPEED, false, 22, -10, -4, 25, -7, -1, true),
            new Table(SpinEffect.DECISIVE, false, 28, -15, -7, 31, -12, -4, true),
            new Table(SpinEffect.HARDSHIP, false, 8, -6, -2, 11, -3, 0, true),
            new Table(SpinEffect.BONUS, true, 30, -4, -1, 33, -1, 0, true),
            new Table(SpinEffect.SAFE, true, 20, -2, 0, 23, 0, 0, false),
            new Table(SpinEffect.BREAKTHROUGH, true, 30, -12, -6, 33, -9, -3, true),
            new Table(SpinEffect.SPEED, true, 35, -16, -8, 38, -13, -5, true),
            new Table(SpinEffect.DECISIVE, true, 40, -22, -10, 43, -19, -7, true));
    }
    static Stream<Arguments> outcomes() {
        return tables().flatMap(t -> Stream.of(
            Arguments.of(t, AnswerStatus.CORRECT, t.correct(), t.momentumCorrect(), true, false, 1500L),
            Arguments.of(t, AnswerStatus.WRONG, t.wrong(), t.recoveryWrong(), false, true, 2500L),
            Arguments.of(t, AnswerStatus.NO_ANSWER, t.noAnswer(), t.recoveryNoAnswer(), false, t.noAnswerConsumesRecovery(), 40000L)));
    }
    PlayerScore player(int score, int win, int lose, boolean momentum, boolean recovery) {
        return new PlayerScore(PlayerState.PLAYING, score, 1000, win, lose, momentum, recovery);
    }
    ScoringInput input(PlayerScore p, AnswerStatus outcome, SpinEffect spin, boolean star, long measuredTime) {
        return new ScoringInput(p, outcome, spin, star, outcome == AnswerStatus.NO_ANSWER ? null : measuredTime);
    }

    @ParameterizedTest @MethodSource("outcomes")
    void allThirteenModesAndThreeOutcomesMatchOverview(Table table, AnswerStatus outcome, int base,
            int modified, boolean consumesMomentum, boolean consumesRecovery, long time) {
        var result = engine.calculate(input(player(100, 0, 0, false, false), outcome, table.spin(), table.star(), time));
        assertThat(result.outcome()).isEqualTo(outcome);
        assertThat(result.baseDelta()).isEqualTo(base); assertThat(result.scoreDelta()).isEqualTo(base);
        assertThat(result.playerAfter().score()).isEqualTo(100 + base);
        assertThat(result.answerTimeMs()).isEqualTo(time); assertThat(result.playerAfter().totalAnswerTimeMs()).isEqualTo(1000 + time);
        assertThat(result.playerAfter().state()).isEqualTo(PlayerState.PLAYING);
        assertThat(result.momentumConsumed()).isFalse(); assertThat(result.recoveryConsumed()).isFalse();
    }
    @ParameterizedTest @MethodSource("outcomes")
    void coexistenceAndModifiersAcrossTheWholeTable(Table table, AnswerStatus outcome, int base,
            int modified, boolean consumesMomentum, boolean consumesRecovery, long time) {
        var result = engine.calculate(input(player(100, 0, 0, true, true), outcome, table.spin(), table.star(), time));
        assertThat(result.baseDelta()).isEqualTo(base); assertThat(result.scoreDelta()).isEqualTo(modified);
        assertThat(result.playerAfter().score()).isEqualTo(100 + modified);
        assertThat(result.momentumConsumed()).isEqualTo(consumesMomentum); assertThat(result.recoveryConsumed()).isEqualTo(consumesRecovery);
        assertThat(result.playerAfter().momentum()).isEqualTo(!consumesMomentum); assertThat(result.playerAfter().recovery()).isEqualTo(!consumesRecovery);
        assertThat(result.momentumGranted()).isFalse(); assertThat(result.recoveryGranted()).isFalse();
        assertThat(result.playerAfter().totalAnswerTimeMs()).isEqualTo(1000 + time);
    }
    @ParameterizedTest @MethodSource("tables")
    void spinAndStarNeverChangeStreakCountingAndNewEffectDoesNotAlterTriggerQuestion(Table table) {
        var win = engine.calculate(input(player(100, 4, 0, false, false), AnswerStatus.CORRECT, table.spin(), table.star(), 1500));
        assertThat(win.scoreDelta()).isEqualTo(table.correct()); assertThat(win.playerAfter().winStreak()).isZero();
        assertThat(win.playerAfter().loseStreak()).isZero(); assertThat(win.momentumGranted()).isTrue(); assertThat(win.playerAfter().momentum()).isTrue();
        var lose = engine.calculate(input(player(100, 0, 4, false, false), AnswerStatus.WRONG, table.spin(), table.star(), 1500));
        assertThat(lose.scoreDelta()).isEqualTo(table.wrong()); assertThat(lose.playerAfter().loseStreak()).isZero();
        assertThat(lose.playerAfter().winStreak()).isZero(); assertThat(lose.recoveryGranted()).isTrue(); assertThat(lose.playerAfter().recovery()).isTrue();
        var missing = engine.calculate(input(player(100, 3, 0, false, false), AnswerStatus.NO_ANSWER, table.spin(), table.star(), 0));
        assertThat(missing.playerAfter().winStreak()).isZero(); assertThat(missing.playerAfter().loseStreak()).isZero();
        assertThat(missing.momentumGranted()).isFalse(); assertThat(missing.recoveryGranted()).isFalse();
        assertThat(missing.answerTimeMs()).isEqualTo(40000);
    }
    @Test void initialTwentyFiveWrongReachZeroThenRecoveryCannotSaveSixthWrong() {
        var state = engine.initialPlayer();
        assertThat(state).isEqualTo(new PlayerScore(PlayerState.PLAYING, 20, 0, 0, 0, false, false));
        int[] expectedScores = {16, 12, 8, 4, 0};
        for (int index = 0; index < expectedScores.length; index++) {
            var result = engine.calculate(input(state, AnswerStatus.WRONG, null, false, 1500));
            assertThat(result.scoreDelta()).isEqualTo(-4); assertThat(result.playerAfter().score()).isEqualTo(expectedScores[index]);
            assertThat(result.playerAfter().state()).isEqualTo(PlayerState.PLAYING);
            assertThat(result.recoveryGranted()).isEqualTo(index == 4); state = result.playerAfter();
        }
        assertThat(state.loseStreak()).isZero(); assertThat(state.recovery()).isTrue();
        var sixth = engine.calculate(input(state, AnswerStatus.WRONG, null, false, 1500));
        assertThat(sixth.baseDelta()).isEqualTo(-4); assertThat(sixth.scoreDelta()).isEqualTo(-1);
        assertThat(sixth.playerAfter().score()).isEqualTo(-1); assertThat(sixth.eliminatedNow()).isTrue();
        assertThat(sixth.playerAfter().state()).isEqualTo(PlayerState.ELIMINATED); assertThat(sixth.recoveryConsumed()).isTrue();
        assertThat(sixth.playerAfter().loseStreak()).isZero(); assertThat(sixth.recoveryGranted()).isFalse();
        assertThat(sixth.playerAfter().totalAnswerTimeMs()).isEqualTo(9000);
    }
    @ParameterizedTest @CsvSource({
        "NO_ANSWER,,false,-1,0,true", "WRONG,,false,-4,-1,true",
        "WRONG,BREAKTHROUGH,false,-7,-4,true", "WRONG,DECISIVE,true,-22,-19,true",
        "NO_ANSWER,SAFE,false,0,0,false"
    })
    void namedRecoveryBoundariesUseOriginalPenaltyAndConsumeAtFinalZero(AnswerStatus outcome, SpinEffect spin,
            boolean star, int base, int expected, boolean consumed) {
        var result = engine.calculate(input(player(100, 0, 0, false, true), outcome, spin, star, 1500));
        assertThat(result.baseDelta()).isEqualTo(base); assertThat(result.scoreDelta()).isEqualTo(expected);
        assertThat(result.recoveryConsumed()).isEqualTo(consumed); assertThat(result.playerAfter().recovery()).isEqualTo(!consumed);
    }
    @Test void wrongToCorrectCorrectToWrongAndNoAnswerResetOppositeStreak() {
        var correct = engine.calculate(input(player(100, 0, 3, false, false), AnswerStatus.CORRECT, null, false, 1500));
        assertThat(correct.playerAfter().winStreak()).isEqualTo(1); assertThat(correct.playerAfter().loseStreak()).isZero();
        var wrong = engine.calculate(input(player(100, 3, 0, false, false), AnswerStatus.WRONG, null, false, 1500));
        assertThat(wrong.playerAfter().winStreak()).isZero(); assertThat(wrong.playerAfter().loseStreak()).isEqualTo(1);
        var noAnswer = engine.calculate(input(player(100, 0, 3, true, true), AnswerStatus.NO_ANSWER, SpinEffect.SAFE, true, 0));
        assertThat(noAnswer.playerAfter().winStreak()).isZero(); assertThat(noAnswer.playerAfter().loseStreak()).isZero();
        assertThat(noAnswer.playerAfter().momentum()).isTrue(); assertThat(noAnswer.playerAfter().recovery()).isTrue();
    }
    @Test void momentumWaitsForCorrectAndIsUsedOnce() {
        var wrong = engine.calculate(input(player(100, 0, 0, true, false), AnswerStatus.WRONG, null, false, 1000));
        assertThat(wrong.scoreDelta()).isEqualTo(-4); assertThat(wrong.playerAfter().momentum()).isTrue();
        var noAnswer = engine.calculate(input(wrong.playerAfter(), AnswerStatus.NO_ANSWER, null, false, 0));
        assertThat(noAnswer.scoreDelta()).isEqualTo(-1); assertThat(noAnswer.playerAfter().momentum()).isTrue();
        var correct = engine.calculate(input(noAnswer.playerAfter(), AnswerStatus.CORRECT, null, false, 1000));
        assertThat(correct.scoreDelta()).isEqualTo(13); assertThat(correct.playerAfter().momentum()).isFalse();
        assertThat(engine.calculate(input(correct.playerAfter(), AnswerStatus.CORRECT, null, false, 1000)).scoreDelta()).isEqualTo(10);
    }
    @Test void fiveCorrectGrantOneMomentumThenNextCorrectGetsBonus() {
        var state = engine.initialPlayer();
        for (int expected : new int[] {30, 40, 50, 60, 70}) {
            var result = engine.calculate(input(state, AnswerStatus.CORRECT, null, false, 1000));
            assertThat(result.playerAfter().score()).isEqualTo(expected); assertThat(result.scoreDelta()).isEqualTo(10); state = result.playerAfter();
        }
        assertThat(state.winStreak()).isZero(); assertThat(state.momentum()).isTrue();
        var next = engine.calculate(input(state, AnswerStatus.CORRECT, null, false, 1000));
        assertThat(next.playerAfter().score()).isEqualTo(83); assertThat(next.playerAfter().momentum()).isFalse();
    }
    @Test void consumeThenRegrantCannotStackOrApplyTwiceOnTrigger() {
        var win = engine.calculate(input(player(100, 4, 0, true, true), AnswerStatus.CORRECT, null, false, 1500));
        assertThat(win.scoreDelta()).isEqualTo(13); assertThat(win.momentumConsumed()).isTrue(); assertThat(win.momentumGranted()).isTrue();
        assertThat(win.playerAfter().momentum()).isTrue(); assertThat(win.playerAfter().recovery()).isTrue();
        assertThat(engine.calculate(input(win.playerAfter(), AnswerStatus.CORRECT, null, false, 1500)).scoreDelta()).isEqualTo(13);
        var lose = engine.calculate(input(player(100, 0, 4, true, true), AnswerStatus.WRONG, null, false, 1500));
        assertThat(lose.scoreDelta()).isEqualTo(-1); assertThat(lose.recoveryConsumed()).isTrue(); assertThat(lose.recoveryGranted()).isTrue();
        assertThat(lose.playerAfter().recovery()).isTrue(); assertThat(lose.playerAfter().momentum()).isTrue();
        assertThat(engine.calculate(input(lose.playerAfter(), AnswerStatus.WRONG, null, false, 1500)).scoreDelta()).isEqualTo(-1);
    }
    @Test void bothEffectsCanBeEarnedAndRetainedTogether() {
        var state = engine.initialPlayer();
        for (int i = 0; i < 5; i++) state = engine.calculate(input(state, AnswerStatus.CORRECT, null, false, 1000)).playerAfter();
        for (int i = 0; i < 5; i++) state = engine.calculate(input(state, AnswerStatus.WRONG, null, false, 1000)).playerAfter();
        assertThat(state.score()).isEqualTo(50); assertThat(state.momentum()).isTrue(); assertThat(state.recovery()).isTrue();
        assertThat(state.winStreak()).isZero(); assertThat(state.loseStreak()).isZero();
    }
    @ParameterizedTest @CsvSource({"3,WRONG,0,4,-1,1500", "0,NO_ANSWER,3,0,-1,40000"})
    void eliminationFreezesPriorStreaksAndCannotGrantNewEffect(int score, AnswerStatus outcome,
            int win, int lose, int expectedScore, long expectedTime) {
        var before = player(score, win, lose, true, false);
        var result = engine.calculate(input(before, outcome, null, false, 1500));
        assertThat(result.playerAfter().score()).isEqualTo(expectedScore); assertThat(result.eliminatedNow()).isTrue();
        assertThat(result.playerAfter().winStreak()).isEqualTo(win); assertThat(result.playerAfter().loseStreak()).isEqualTo(lose);
        assertThat(result.momentumGranted()).isFalse(); assertThat(result.recoveryGranted()).isFalse();
        assertThat(result.playerAfter().momentum()).isTrue(); assertThat(result.playerAfter().recovery()).isFalse();
        assertThat(result.answerTimeMs()).isEqualTo(expectedTime); assertThat(result.playerAfter().totalAnswerTimeMs()).isEqualTo(1000 + expectedTime);
        assertThat(before.score()).isEqualTo(score); // Input immutable even on the eliminating question.
    }
    @Test void recoveryAtNegativeOneCanKeepZeroAliveAndConsumes() {
        var result = engine.calculate(input(player(0, 0, 0, false, true), AnswerStatus.NO_ANSWER, null, false, 0));
        assertThat(result.playerAfter().score()).isZero(); assertThat(result.playerAfter().state()).isEqualTo(PlayerState.PLAYING);
        assertThat(result.recoveryConsumed()).isTrue(); assertThat(result.playerAfter().recovery()).isFalse(); assertThat(result.eliminatedNow()).isFalse();
    }
    @Test void recoveryReducingWrongToZeroStillCountsWrongWhileUnpenalizedNoAnswerResets() {
        var wrong = engine.calculate(input(player(100, 0, 3, false, true), AnswerStatus.WRONG, SpinEffect.SAFE, true, 1500));
        assertThat(wrong.baseDelta()).isEqualTo(-2); assertThat(wrong.scoreDelta()).isZero();
        assertThat(wrong.playerAfter().loseStreak()).isEqualTo(4); assertThat(wrong.recoveryConsumed()).isTrue();
        assertThat(wrong.playerAfter().recovery()).isFalse(); assertThat(wrong.recoveryGranted()).isFalse();
        var noAnswer = engine.calculate(input(player(100, 0, 4, false, true), AnswerStatus.NO_ANSWER, SpinEffect.SAFE, true, 0));
        assertThat(noAnswer.baseDelta()).isZero(); assertThat(noAnswer.scoreDelta()).isZero();
        assertThat(noAnswer.playerAfter().loseStreak()).isZero(); assertThat(noAnswer.recoveryConsumed()).isFalse();
        assertThat(noAnswer.playerAfter().recovery()).isTrue(); assertThat(noAnswer.recoveryGranted()).isFalse();
    }
    @ParameterizedTest @EnumSource(value=AnswerStatus.class, names={"CORRECT", "WRONG", "NO_ANSWER"})
    void eliminatedPlayersCannotBeScoredAgainAndHardshipStarIsAlwaysRejected(AnswerStatus outcome) {
        var eliminated = new PlayerScore(PlayerState.ELIMINATED, -1, 12345, 0, 4, true, false);
        assertThatThrownBy(() -> engine.calculate(input(eliminated, outcome, null, false, 1000))).isInstanceOf(IllegalStateException.class);
        assertThat(eliminated).isEqualTo(new PlayerScore(PlayerState.ELIMINATED, -1, 12345, 0, 4, true, false));
        assertThatThrownBy(() -> engine.calculate(input(player(100, 0, 0, false, false), outcome, SpinEffect.HARDSHIP, true, 1000)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not allowed");
    }
    @Test void acceptedUnscoredIsNeverTreatedAsWrongOrNoAnswer() {
        assertThatThrownBy(() -> new ScoringInput(engine.initialPlayer(), AnswerStatus.ACCEPTED_UNSCORED, null, false, 1000L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not been evaluated");
    }
    @Test void deterministicReusableAndNeverMutatesInput() {
        var state = player(100, 4, 0, true, true);
        var input = input(state, AnswerStatus.CORRECT, SpinEffect.DECISIVE, true, 1234);
        var first = engine.calculate(input); assertThat(engine.calculate(input)).isEqualTo(first);
        assertThat(state).isEqualTo(player(100, 4, 0, true, true));
        assertThat(first.scoreDelta()).isEqualTo(43); assertThat(first.playerAfter().score()).isEqualTo(143);
    }
    @Test void answerTimeUsesProvidedMillisecondsAndNoAnswerUsesWholeConfiguredDuration() {
        var answered = engine.calculate(input(engine.initialPlayer(), AnswerStatus.CORRECT, null, false, 39999));
        assertThat(answered.playerAfter().totalAnswerTimeMs()).isEqualTo(39999);
        assertThat(engine.calculate(input(engine.initialPlayer(), AnswerStatus.WRONG, null, false, 0)).answerTimeMs()).isZero();
        var missing = engine.calculate(new ScoringInput(engine.initialPlayer(), AnswerStatus.NO_ANSWER, null, false, null));
        assertThat(missing.answerTimeMs()).isEqualTo(40000);
        assertThatThrownBy(() -> engine.calculate(input(engine.initialPlayer(), AnswerStatus.CORRECT, null, false, 40000))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoringInput(engine.initialPlayer(), AnswerStatus.CORRECT, null, false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoringInput(engine.initialPlayer(), AnswerStatus.WRONG, null, false, -1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoringInput(engine.initialPlayer(), AnswerStatus.NO_ANSWER, null, false, 0L)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void overflowAndUnsupportedRulesFailWithoutReturningPartialState() {
        var highScore = player(Integer.MAX_VALUE, 0, 0, false, false);
        assertThatThrownBy(() -> engine.calculate(input(highScore, AnswerStatus.CORRECT, null, false, 1000))).isInstanceOf(ArithmeticException.class);
        var highTime = new PlayerScore(PlayerState.PLAYING, 100, Long.MAX_VALUE, 0, 0, false, false);
        assertThatThrownBy(() -> engine.calculate(input(highTime, AnswerStatus.WRONG, null, false, 1000))).isInstanceOf(ArithmeticException.class);
        var r = GameplayRulesSnapshot.forGame(10, 40000, 5000);
        var altered = new GameplayRulesSnapshot(r.schemaVersion(), r.questionCount(), r.questionDurationMs(), r.decisionDurationMs(),
                r.initialScore(), r.spinCredits(), r.starCredits(), new GameplayRulesSnapshot.Score(999, -4, -1), r.starOnly(), r.spins(), r.streaks(),
                r.spinWithoutReplacement(), r.spinBeforeStarOnly(), r.eliminationRule(), r.rankingRule(), r.endReasonPriority(), r.allEliminatedRankOneAreWinners());
        assertThatThrownBy(() -> new ScoreEngine(altered)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
    }
}
