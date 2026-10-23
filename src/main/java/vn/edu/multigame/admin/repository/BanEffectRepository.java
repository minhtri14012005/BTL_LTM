package vn.edu.multigame.admin.repository;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class BanEffectRepository {
    public record Intent(long id, long actionId, long userId, long version, String type, Long game, Long room,
            Long member, Long generation, Long cutoff, String reservation, Long reservationVersion, long effectiveAt,
            String state) {
    }

    private final JdbcTemplate jdbc;

    public BanEffectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Intent get(long id) {
        return jdbc.queryForObject("select * from account_effect_intent where id=?",
                (r, n) -> new Intent(r.getLong("id"), r.getLong("action_id"), r.getLong("user_id"),
                        r.getLong("ban_version"), r.getString("effect_type"),
                        r.getObject("game_session_id", Long.class), r.getObject("room_id", Long.class),
                        r.getObject("membership_row_id", Long.class), r.getObject("membership_generation", Long.class),
                        r.getObject("auth_cutoff_epoch", Long.class), r.getString("reservation_id"),
                        r.getObject("reservation_version", Long.class), r.getLong("effective_at_ms"),
                        r.getString("state")),
                id);
    }

    public List<Long> pending() {
        return jdbc.queryForList(
                "select id from account_effect_intent where state='PENDING' and retry_after_ms<=? order by id limit 64",
                Long.class, System.currentTimeMillis());
    }

    public boolean done(long id) {
        return "DONE".equals(
                jdbc.queryForObject("select state from account_effect_intent where id=? for update", String.class, id));
    }

    public void complete(long id) {
        jdbc.update("update account_effect_intent set state='DONE',completed_at_ms=? where id=? and state='PENDING'",
                System.currentTimeMillis(), id);
    }

    public void retry(long id) {
        jdbc.update(
                "update account_effect_intent set attempts=least(attempts+1,2147483647),retry_after_ms=? where id=? and state='PENDING'",
                System.currentTimeMillis() + 2000, id);
    }
}
