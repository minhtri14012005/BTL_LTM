package vn.edu.multigame.leaderboard.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.achievements.dto.PlayerProfileView.Stats;
import vn.edu.multigame.achievements.repository.ProfileRepository;
import vn.edu.multigame.achievements.service.ProfileReadService;
import vn.edu.multigame.leaderboard.dto.LeaderboardView;

/** A14 exact rational comparison; ID stabilizes pagination, never breaks a competitive tie. */
@Repository @Profile("mysql")
public class LeaderboardRepository {
    private final JdbcTemplate jdbc;
    public LeaderboardRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String VISIBLE = ProfileRepository.FACTS+", visible as (select t.*,u.display_name from totals t join app_user u on u.id=t.user_id where "+ProfileReadService.PUBLIC_ACCOUNT+") ";
    // Sums are MySQL DECIMAL. Cross-products compare the ratios without rounded percentages.
    // Qualified games always have positive participation. No unranked/zero-game users in visible.
    private static final String RANKED = VISIBLE+"""
        , ranked as (
          select t.*, 1+(select count(*) from visible b where b.cups>t.cups or
            (b.cups=t.cups and (b.wins*t.games>t.wins*b.games or
              (b.wins*t.games=t.wins*b.games and b.correct*t.participated>t.correct*b.participated)))) standing
          from visible t
        )
        """;
    private static final org.springframework.jdbc.core.RowMapper<LeaderboardView.Entry> ENTRY=(r,n)->
            new LeaderboardView.Entry(r.getLong("standing"),r.getLong("user_id"),r.getString("display_name"),
                    Stats.of(r.getLong("games"),r.getLong("wins"),r.getLong("cups"),r.getLong("correct"),r.getLong("participated")));
    public LeaderboardView read(long userId,int page,int size,long at) {
        long count=jdbc.queryForObject(VISIBLE+"select count(*) from visible",Long.class,at);
        var items=jdbc.query(RANKED+"select * from ranked order by standing,user_id limit ? offset ?",ENTRY,at,size,(long)page*size);
        var own=jdbc.query(RANKED+"select * from ranked where user_id=?",ENTRY,at,userId);
        return new LeaderboardView(items,page,size,count,own.isEmpty()?null:own.getFirst(),at);
    }
}
