package vn.edu.multigame.game.engine;

import vn.edu.multigame.game.enums.AnswerStatus;

/** Calculation only: the caller must commit all players together before exposing this result. */
public record ScoringResult(AnswerStatus outcome, int baseDelta, int scoreDelta, long answerTimeMs,
        PlayerScore playerAfter, boolean momentumConsumed, boolean recoveryConsumed,
        boolean momentumGranted, boolean recoveryGranted, boolean eliminatedNow, int ruleDelta) {
    public ScoringResult(AnswerStatus outcome,int baseDelta,int scoreDelta,long answerTimeMs,PlayerScore playerAfter,
            boolean momentumConsumed,boolean recoveryConsumed,boolean momentumGranted,boolean recoveryGranted,boolean eliminatedNow) {
        this(outcome,baseDelta,scoreDelta,answerTimeMs,playerAfter,momentumConsumed,recoveryConsumed,momentumGranted,recoveryGranted,eliminatedNow,scoreDelta);
    }
}
