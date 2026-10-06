package vn.edu.quiz.game.dto;

import vn.edu.quiz.game.enums.SpinEffect;

import java.util.Map;

/** Versioned immutable data from Overview; this is not a scoring engine. */
public record GameplayRulesSnapshot(
        int schemaVersion, int questionCount, long questionDurationMs, long decisionDurationMs,
        int initialScore, int spinCredits, int starCredits, Score normal, Score starOnly,
        Map<SpinEffect, SpinRule> spins, StreakRules streaks,
        boolean spinWithoutReplacement, boolean spinBeforeStarOnly,
        String eliminationRule, String rankingRule, String endReasonPriority,
        boolean allEliminatedRankOneAreWinners) {

    public GameplayRulesSnapshot {
        if (schemaVersion != 1 || questionCount < 10 || questionCount > 50
                || questionDurationMs <= 0 || decisionDurationMs <= 0) {
            throw new IllegalArgumentException("Invalid snapshot version/count/duration");
        }
        spins = Map.copyOf(spins);
    }

    public record Score(int correct, int wrong, int noAnswer) {}
    public record SpinRule(int weight, Score normal, Score withStar, boolean starAllowed) {}
    public record StreakRules(int threshold, int momentumBonus, int recoveryReduction,
            int recoveryPenaltyCap, boolean recoveryUsesBasePenalty,
            boolean recoveryConsumedWhenFinalPenaltyZero, boolean newEffectAppliesOnTrigger,
            boolean effectsStack, boolean noAnswerResetsBothStreaks,
            boolean eliminationBeforeStreakUpdate) {}

    public static GameplayRulesSnapshot forGame(int count, long questionMs, long decisionMs) {
        return new GameplayRulesSnapshot(1, count, questionMs, decisionMs,
                20, count / 10, 1, new Score(10, -4, -1), new Score(25, -14, -7),
                Map.of(
                    SpinEffect.BONUS, new SpinRule(25, new Score(15, -4, -1), new Score(30, -4, -1), true),
                    SpinEffect.SAFE, new SpinRule(20, new Score(8, -2, 0), new Score(20, -2, 0), true),
                    SpinEffect.BREAKTHROUGH, new SpinRule(25, new Score(18, -7, -3), new Score(30, -12, -6), true),
                    SpinEffect.SPEED, new SpinRule(15, new Score(22, -10, -4), new Score(35, -16, -8), true),
                    SpinEffect.DECISIVE, new SpinRule(10, new Score(28, -15, -7), new Score(40, -22, -10), true),
                    SpinEffect.HARDSHIP, new SpinRule(5, new Score(8, -6, -2), null, false)),
                new StreakRules(5, 3, 3, 0, true, true, false, false, true, true),
                true, true, "SCORE_LT_ZERO", "SCORE_DESC_TIME_ASC_COMPETITION",
                "ALL_ELIMINATED_OR_ONE_SURVIVOR_BEFORE_COMPLETED", true);
    }
}
