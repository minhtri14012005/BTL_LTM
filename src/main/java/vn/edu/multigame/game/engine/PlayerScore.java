package vn.edu.multigame.game.engine;

import java.util.Objects;
import vn.edu.multigame.game.enums.PlayerState;

/** Immutable scoring state; resources, identity, connection and persistence belong to orchestration. */
public record PlayerScore(PlayerState state, int score, long totalAnswerTimeMs,
        int winStreak, int loseStreak, boolean momentum, boolean recovery) {
    public PlayerScore {
        Objects.requireNonNull(state, "state");
        if (totalAnswerTimeMs < 0 || winStreak < 0 || winStreak > 4 || loseStreak < 0 || loseStreak > 4
                || (winStreak > 0 && loseStreak > 0)
                || (state == PlayerState.PLAYING && score < 0)
                || (state == PlayerState.ELIMINATED && score >= 0)) {
            throw new IllegalArgumentException("Inconsistent player scoring state");
        }
    }
}
