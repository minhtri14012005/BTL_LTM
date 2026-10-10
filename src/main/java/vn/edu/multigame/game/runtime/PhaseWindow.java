package vn.edu.multigame.game.runtime;

import java.util.Objects;
import vn.edu.multigame.game.enums.Phase;

/** Pure phase identity/time data. Create when the processor actually opens the phase, not at ingress. */
public record PhaseWindow(int questionIndex, Phase phase, long token, long openedAtMs,
        long deadlineMs, long openedEpochMs, long deadlineEpochMs) {
    public PhaseWindow {
        Objects.requireNonNull(phase, "phase");
        if (questionIndex < 1 || token < 1 || deadlineMs <= openedAtMs
                || Math.subtractExact(deadlineMs, openedAtMs) != Math.subtractExact(deadlineEpochMs, openedEpochMs)) {
            throw new IllegalArgumentException("Invalid phase window");
        }
    }

    public static PhaseWindow open(int questionIndex, Phase phase, long token,
            long monotonicMs, long epochMs, long durationMs) {
        if (durationMs <= 0) throw new IllegalArgumentException("Duration must be positive");
        return new PhaseWindow(questionIndex, phase, token, monotonicMs,
                Math.addExact(monotonicMs, durationMs), epochMs, Math.addExact(epochMs, durationMs));
    }

    public boolean accepts(long receivedAtMs) { return receivedAtMs >= openedAtMs && receivedAtMs < deadlineMs; }
    public boolean matches(int question, Phase expectedPhase, long expectedToken) {
        return questionIndex == question && phase == expectedPhase && token == expectedToken;
    }
    public long remainingMs(long monotonicMs) { return Math.max(0, Math.subtractExact(deadlineMs, monotonicMs)); }
}
