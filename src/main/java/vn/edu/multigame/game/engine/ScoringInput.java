package vn.edu.multigame.game.engine;

import java.util.Objects;
import vn.edu.multigame.game.enums.AnswerStatus;
import vn.edu.multigame.game.enums.SpinEffect;

/** Outcome is determined by the caller at scoring, never by an Answer ACCEPT or client payload. */
public record ScoringInput(PlayerScore player, AnswerStatus outcome, SpinEffect spin,
        boolean starSelected, Long answeredTimeMs) {
    public ScoringInput {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(outcome, "outcome");
        if (outcome == AnswerStatus.ACCEPTED_UNSCORED) {
            throw new IllegalArgumentException("Accepted answer has not been evaluated yet");
        }
        if (outcome == AnswerStatus.NO_ANSWER ? answeredTimeMs != null
                : answeredTimeMs == null || answeredTimeMs < 0) {
            throw new IllegalArgumentException("Only answered outcomes have measured answer time");
        }
    }
}
