package vn.edu.multigame.game.repository;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.game.entity.PlayerSession;

/** Append-only OPEN identities and DECISION resource evidence; not a score engine. */
@Repository @Profile("mysql")
public class QuestionParticipationStore {
    private final JdbcTemplate jdbc;
    public QuestionParticipationStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void capture(long game,long question,long opened,Collection<PlayerSession> players){
        for(var p:players)jdbc.update("insert into game_question_participation(game_session_id,game_question_id,player_session_id,opened_at_ms) values(?,?,?,?)",game,question,p.getId(),opened);
    }
    public Set<Long> players(long question){return Set.copyOf(jdbc.queryForList("select player_session_id from game_question_participation where game_question_id=?",Long.class,question));}
    public List<vn.edu.multigame.game.dto.response.GameHistoryDetail.Decision> decisions(long game){
        return jdbc.query("select p.user_id,d.* from game_decision_action d join player_session p on p.id=d.player_session_id where d.game_session_id=? order by d.question_index,p.user_id,d.action",(r,n)->new vn.edu.multigame.game.dto.response.GameHistoryDetail.Decision(r.getLong("user_id"),r.getInt("question_index"),r.getString("action"),r.getString("spin_effect")==null?null:vn.edu.multigame.game.enums.SpinEffect.valueOf(r.getString("spin_effect")),r.getInt("remaining_spins"),r.getBoolean("star_available"),r.getBoolean("star_selected"),r.getLong("committed_at_ms")),game);
    }
    public List<vn.edu.multigame.game.dto.response.GameHistoryDetail.Restriction> restrictions(long game){
        return jdbc.query("select user_id,kind,effective_at_ms from game_participation_restriction where game_session_id=? union all select user_id,'BANNED_BY_ADMIN' as kind,min(effective_at_ms) from account_effect_intent where game_session_id=? and effect_type='GAME' group by user_id order by user_id",(r,n)->new vn.edu.multigame.game.dto.response.GameHistoryDetail.Restriction(r.getLong("user_id"),r.getString("kind"),r.getLong("effective_at_ms")),game,game);
    }
    public void decision(long game,int index,String action,PlayerSession p,long at){
        jdbc.update("insert into game_decision_action(game_session_id,player_session_id,question_index,action,spin_effect,remaining_spins,star_available,star_selected,committed_at_ms) values(?,?,?,?,?,?,?,?,?)",game,p.getId(),index,action,p.getCurrentSpin()==null?null:p.getCurrentSpin().name(),p.getRemainingSpins(),p.getStarAvailable(),p.getStarSelected(),at);
    }
}
