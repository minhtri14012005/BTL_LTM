package vn.edu.multigame.achievements.dto;

import java.util.List;

/** Explicit allowlist: never serialize an account, moderation evidence or gameplay snapshot. */
public record PlayerProfileView(long userId, String displayName, Stats stats, List<Badge> badges) {
    public record PublicProfile(long userId, String displayName, Stats stats) {}
    public record Stats(long officialGames, long officialWins, long cups, long correct, long participated,
                        Double winRate, Double accuracy) {
        public static Stats of(long games, long wins, long cups, long correct, long participated) {
            return new Stats(games, wins, cups, correct, participated,
                    games == 0 ? null : (double) wins / games,
                    participated == 0 ? null : (double) correct / participated);
        }
    }
    public record Badge(String type, String modeKey, long count, long lastEarnedAtMs) {}
    public record EarnedAward(long awardId, long gameSessionId, String type, String modeKey, long earnedAtMs) {}
    public record AwardPage(List<EarnedAward> items, int page, int size, long totalElements) {}
}
