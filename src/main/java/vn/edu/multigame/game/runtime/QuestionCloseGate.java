package vn.edu.multigame.game.runtime;

import java.util.HashSet;
import java.util.Set;

/** One-question guard, owned by the serial game handler. No scoring, persistence or connection filtering. */
public final class QuestionCloseGate {
    private final Set<Long> eligiblePlayers;
    private final Set<Long> answered = new HashSet<>();
    private boolean closed;

    public QuestionCloseGate(Set<Long> eligiblePlayers) {
        this.eligiblePlayers = Set.copyOf(eligiblePlayers);
        if (this.eligiblePlayers.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("Expected valid player identities");
        }
    }

    /** Call only after identity/state/deadline/answer validation (and persistence in Task 8). */
    public boolean recordValidAnswer(long playerId) {
        return !closed && eligiblePlayers.contains(playerId) && answered.add(playerId);
    }
    public boolean closeIfAllAnswered() {
        return allAnswered() && close();
    }
    public boolean allAnswered() { return !closed && !eligiblePlayers.isEmpty() && answered.size()==eligiblePlayers.size(); }
    /** True grants the handler the single close/scoring attempt. This is not a DB exactly-once guarantee. */
    public boolean close() {
        if (closed) return false;
        closed = true;
        return true;
    }
    public boolean isClosed() { return closed; }
}
