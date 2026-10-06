package vn.edu.quiz.game.engine;

import vn.edu.quiz.game.enums.AnswerStatus;

/** Calculation only: the caller must commit all players together before exposing this result. */
public record ScoringResult(AnswerStatus outcome, int baseDelta, int scoreDelta, long answerTimeMs,
        PlayerScore playerAfter, boolean momentumConsumed, boolean recoveryConsumed,
        boolean momentumGranted, boolean recoveryGranted, boolean eliminatedNow) {}
