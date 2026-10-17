package vn.edu.multigame.achievements.repository;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.achievements.dto.PlayerProfileView.*;

/** Query committed immutable T11 facts. No counters, projections, locks or writes. */
@Repository @Profile("mysql")
public class ProfileRepository {
    private final JdbcTemplate jdbc;
    public ProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Scope is repeated defensively, independently of current occupancy or account sanctions.
    public static final String QUALIFIED = """
        from game_award_decision d join game_session g on g.id=d.game_session_id
        where d.state='DECIDED' and g.status='FINISHED' and g.end_reason='COMPLETED'
          and g.game_type='NORMAL' and g.schema_version=2 and g.rules_version=2
          and g.award_policy_snapshot is not null
        """;
    public static final String FACTS = """
        with qualified as (select d.game_session_id %s),
        participation as (
          select p.user_id, sum(p.official_game_delta) games, sum(p.official_win_delta) wins,
            sum(p.correct_count) correct, sum(p.participated_count) participated
          from game_award_participant p join qualified q on q.game_session_id=p.game_session_id
          where p.eligible=1 and p.official_game_delta=1 group by p.user_id
        ), cups as (
          select a.user_id, sum(a.award_value) cups from achievement_award a
          join qualified q on q.game_session_id=a.game_session_id
          join game_award_participant p on p.game_session_id=a.game_session_id and p.user_id=a.user_id
          where a.award_type='CUP' and p.eligible=1 and p.official_game_delta=1 group by a.user_id
        ), totals as (
          select p.*, coalesce(c.cups,0) cups from participation p left join cups c on c.user_id=p.user_id
        )
        """.formatted(QUALIFIED);

    public Stats stats(long userId) {
        var rows=jdbc.query(FACTS+"select * from totals where user_id=?",(r,n)->Stats.of(
                r.getLong("games"),r.getLong("wins"),r.getLong("cups"),r.getLong("correct"),r.getLong("participated")),userId);
        return rows.isEmpty()?Stats.of(0,0,0,0,0):rows.getFirst();
    }
    private static final String AWARDS = """
        from achievement_award a join game_award_participant p
          on p.game_session_id=a.game_session_id and p.user_id=a.user_id
        join (%s) q on q.game_session_id=a.game_session_id
        where a.user_id=? and p.eligible=1 and p.official_game_delta=1
        """.formatted("select d.game_session_id "+QUALIFIED);
    public List<Badge> badges(long userId) {
        return jdbc.query("select a.award_type,a.mode_key,count(*) amount,max(a.earned_at_ms) latest "+AWARDS+
                " group by a.award_type,a.mode_key order by a.award_type,a.mode_key",(r,n)->
                new Badge(r.getString("award_type"),r.getString("mode_key"),r.getLong("amount"),r.getLong("latest")),userId);
    }
    public AwardPage awards(long userId,int page,int size) {
        long total=jdbc.queryForObject("select count(*) "+AWARDS,Long.class,userId);
        var items=jdbc.query("select a.id,a.game_session_id,a.award_type,a.mode_key,a.earned_at_ms "+AWARDS+
                " order by a.earned_at_ms desc,a.id desc limit ? offset ?",(r,n)->new EarnedAward(r.getLong("id"),
                r.getLong("game_session_id"),r.getString("award_type"),r.getString("mode_key"),r.getLong("earned_at_ms")),userId,size,(long)page*size);
        return new AwardPage(items,page,size,total);
    }
}
