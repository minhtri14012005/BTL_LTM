package vn.edu.multigame.leaderboard.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.achievements.service.ProfileReadService;
import vn.edu.multigame.leaderboard.dto.LeaderboardView;
import vn.edu.multigame.leaderboard.repository.LeaderboardRepository;

@Service @Profile("mysql")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class LeaderboardReadService {
    private final LeaderboardRepository rankings;
    public LeaderboardReadService(LeaderboardRepository rankings) { this.rankings=rankings; }
    public LeaderboardView read(long userId,int page,int size) {
        ProfileReadService.validatePage(page,size);
        return rankings.read(userId,page,size,System.currentTimeMillis());
    }
}
