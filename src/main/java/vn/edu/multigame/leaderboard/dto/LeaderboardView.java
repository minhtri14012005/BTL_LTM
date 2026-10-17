package vn.edu.multigame.leaderboard.dto;

import java.util.List;
import vn.edu.multigame.achievements.dto.PlayerProfileView.Stats;

public record LeaderboardView(List<Entry> items,int page,int size,long totalElements,Entry own,long evaluatedAtMs) {
    public record Entry(long rank,long userId,String displayName,Stats stats) {}
}
