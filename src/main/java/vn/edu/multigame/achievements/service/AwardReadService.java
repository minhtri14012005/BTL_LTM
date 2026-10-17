package vn.edu.multigame.achievements.service;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.achievements.dto.GameAwardsView;
import vn.edu.multigame.achievements.model.AwardEvaluator;
import vn.edu.multigame.game.enums.GameStatus;
import vn.edu.multigame.game.repository.*;
import vn.edu.multigame.game.service.GameFailure;

@Service
@Profile("mysql")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class AwardReadService {
    private final GameSessionRepository games;
    private final GameMemberRepository members;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AwardReadService(GameSessionRepository games, GameMemberRepository members, JdbcTemplate jdbc,
            ObjectMapper json) {
        this.games = games;
        this.members = members;
        this.jdbc = jdbc;
        this.json = json;
    }

    public GameAwardsView read(long user, long gameId) {
        var game = games.findById(gameId).orElseThrow(() -> new GameFailure(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND"));
        if (members.findByGameSessionIdAndUserId(gameId, user).isEmpty())
            throw new GameFailure(HttpStatus.FORBIDDEN, "FORBIDDEN");
        if (game.getStatus() != GameStatus.FINISHED)
            throw GameFailure.conflict("RESULT_NOT_READY");
        String availability = game.getSchemaVersion() != 2 ? "UNSUPPORTED_SCHEMA"
                : !game.getGameType().equals("NORMAL") ? "EXCLUDED_GAME_TYPE"
                        : game.getAwardPolicySnapshot() == null ? "NO_POLICY_SNAPSHOT" : "AVAILABLE";
        if (!availability.equals("AVAILABLE"))
            return new GameAwardsView(gameId, "RESULT_READY", availability, null, false, null, null, null, null,
                    System.currentTimeMillis(), null, List.of(), List.of());
        var rows = jdbc.queryForList("select * from game_award_decision where game_session_id=?", gameId);
        if (rows.isEmpty())
            return new GameAwardsView(gameId, "RESULT_READY", availability, "AWARDS_PENDING", false, 0L,
                    game.getAwardPolicySnapshot().policyId(), null, null, System.currentTimeMillis(), null, List.of(),
                    List.of());
        var d = rows.getFirst();
        String state = (String) d.get("state");
        boolean pending = state.equals("PENDING");
        if (pending)
            return new GameAwardsView(gameId, "RESULT_READY", availability, "AWARDS_PENDING", true, 0L,
                    (String) d.get("policy_version"), null, null, System.currentTimeMillis(), null, List.of(),
                    List.of());
        var participants = jdbc.query(
                "select a.*,m.display_name_snapshot from game_award_participant a join game_member m on m.game_session_id=a.game_session_id and m.user_id=a.user_id where a.game_session_id=? order by coalesce(a.award_rank,2147483647),a.history_rank,a.user_id",
                (r, n) -> new GameAwardsView.Participant(r.getLong("user_id"), r.getString("display_name_snapshot"),
                        r.getInt("history_rank"), r.getObject("award_rank", Integer.class), r.getBoolean("eligible"),
                        r.getBoolean("eligible") ? List.of() : List.of("ADMIN_OR_ACCOUNT_RESTRICTION"),
                        r.getInt("official_game_delta"), r.getInt("official_win_delta")),
                gameId);
        var awards = jdbc.query(
                "select l.*,p.mode_metrics from achievement_award l join game_award_participant p on p.game_session_id=l.game_session_id and p.user_id=l.user_id where l.game_session_id=? order by l.id",
                (r, n) -> new GameAwardsView.Award(r.getLong("id"), r.getLong("user_id"), r.getString("award_type"),
                        r.getString("mode_key"), r.getInt("award_value"), r.getLong("earned_at_ms"),
                        decodeMetrics(r.getString("mode_metrics")), decodeEvidence(r.getString("evidence_snapshot"))),
                gameId);
        var q = new GameAwardsView.Qualification(state.equals("DECIDED"), decodeReasons((String) d.get("reason_codes")),
                num(d, "initial_player_count"), num(d, "eligible_player_count"), num(d, "configured_count"),
                num(d, "opened_count"), num(d, "scored_count"), num(d, "eligible_correct_count"));
        return new GameAwardsView(gameId, "RESULT_READY", availability, "AWARDS_" + state, true,
                ((Number) d.get("revision")).longValue(), (String) d.get("policy_version"),
                ((Number) d.get("eligibility_evaluated_at_ms")).longValue(),
                ((Number) d.get("decided_at_ms")).longValue(), System.currentTimeMillis(), q, participants, awards);
    }

    private int num(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }
    private Map<String,Object> decodeEvidence(String value) {
        if(value==null)return Map.of();
        try{return json.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
        catch(Exception e){throw GameFailure.unavailable();}
    }

    private List<String> decodeReasons(String value) {
        try {
            return json.readValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw GameFailure.unavailable();
        }
    }

    private List<AwardEvaluator.Metric> decodeMetrics(String value) {
        try {
            return json.readValue(value,
                    new com.fasterxml.jackson.core.type.TypeReference<List<AwardEvaluator.Metric>>() {
                    });
        } catch (Exception e) {
            throw GameFailure.unavailable();
        }
    }
}
