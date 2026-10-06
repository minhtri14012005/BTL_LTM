package vn.edu.quiz.game.engine;

import java.util.*;

/** All Player entries, including eliminated players; no spectators, end reason or Winner decision here. */
public final class RankingEngine {
    public record Entry(long userId, int score, long totalAnswerTimeMs) {
        public Entry {
            if (userId <= 0 || totalAnswerTimeMs < 0) throw new IllegalArgumentException("Invalid ranking entry");
        }
    }
    public record RankedPlayer(int rank, Entry player) {}

    public List<RankedPlayer> calculate(List<Entry> players) {
        Objects.requireNonNull(players, "players");
        Set<Long> identities = new HashSet<>();
        for (Entry entry : players) {
            Objects.requireNonNull(entry, "player");
            if (!identities.add(entry.userId())) throw new IllegalArgumentException("Duplicate player in ranking");
        }
        List<Entry> sorted = new ArrayList<>(players);
        // ID orders the display of ties only. It never breaks a scoring/time tie or changes rank.
        sorted.sort(Comparator.comparingInt(Entry::score).reversed()
                .thenComparingLong(Entry::totalAnswerTimeMs).thenComparingLong(Entry::userId));
        List<RankedPlayer> result = new ArrayList<>();
        Entry previous = null;
        int rank = 0;
        for (int index = 0; index < sorted.size(); index++) {
            Entry entry = sorted.get(index);
            if (previous == null || entry.score() != previous.score() || entry.totalAnswerTimeMs() != previous.totalAnswerTimeMs()) rank = index + 1;
            result.add(new RankedPlayer(rank, entry)); previous = entry;
        }
        return List.copyOf(result);
    }
}
