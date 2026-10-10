package vn.edu.multigame.game.engine;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Golden order/ranks from Overview §8.8; no Winner/endReason/lifecycle in this calculator. */
class RankingEngineTest {
    final RankingEngine ranking = new RankingEngine();
    RankingEngine.Entry entry(long userId, int score, long time) { return new RankingEngine.Entry(userId, score, time); }
    @Test void scoreDescendingTimeAscendingAndCompetitionRanksIncludeEliminatedPlayers() {
        var result = ranking.calculate(List.of(entry(6, -5, 1), entry(4, 50, 48000), entry(2, 50, 40000),
                entry(5, 0, 1), entry(3, 50, 40000), entry(1, 60, 99999)));
        assertThat(result.stream().map(row -> row.player().userId())).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(result.stream().map(RankingEngine.RankedPlayer::rank)).containsExactly(1, 2, 2, 4, 5, 6);
    }
    @Test void topTiesAreOneOneThreeRegardlessOfInputOrUserId() {
        var players = List.of(entry(30, 50, 40000), entry(20, 50, 48000), entry(10, 50, 40000));
        var result = ranking.calculate(players);
        assertThat(result.stream().map(RankingEngine.RankedPlayer::rank)).containsExactly(1, 1, 3);
        assertThat(result.stream().map(row -> row.player().userId())).containsExactly(10L, 30L, 20L);
        assertThat(ranking.calculate(List.of(players.get(2), players.get(0), players.get(1)))).isEqualTo(result);
    }
    @Test void multipleTieGroupsAreOneOneThreeThreeFiveAndAllEliminatedStillRank() {
        var result = ranking.calculate(List.of(entry(5, -8, 10), entry(4, -4, 100), entry(3, -4, 100), entry(2, -1, 20), entry(1, -1, 20)));
        assertThat(result.stream().map(RankingEngine.RankedPlayer::rank)).containsExactly(1, 1, 3, 3, 5);
        assertThat(result).hasSize(5); // Deciding official Winners is a later lifecycle concern.
    }
    @Test void doesNotMutateCallerListAndOutputIsImmutable() {
        var input = new ArrayList<>(List.of(entry(2, 10, 2), entry(1, 20, 1)));
        var original = List.copyOf(input); var result = ranking.calculate(input);
        assertThat(input).isEqualTo(original); assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ranking.calculate(List.of())).isEmpty(); assertThat(ranking.calculate(List.of(entry(1, 0, 0))).getFirst().rank()).isEqualTo(1);
    }
    @Test void invalidIdentityTimeAndDuplicatePlayersAreRejected() {
        assertThatThrownBy(() -> entry(0, 10, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(1, 10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ranking.calculate(List.of(entry(1, 20, 10), entry(1, 30, 5)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void comparesIntegerAndLongBoundariesWithoutOverflow() {
        var result = ranking.calculate(List.of(entry(1, Integer.MIN_VALUE, 0), entry(2, Integer.MAX_VALUE, Long.MAX_VALUE), entry(3, Integer.MAX_VALUE, 0)));
        assertThat(result.stream().map(row -> row.player().userId())).containsExactly(3L, 2L, 1L);
        assertThat(result.stream().map(RankingEngine.RankedPlayer::rank)).containsExactly(1, 2, 3);
    }
}
