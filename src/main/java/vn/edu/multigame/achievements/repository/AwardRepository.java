package vn.edu.multigame.achievements.repository;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.achievements.model.*;
import vn.edu.multigame.game.entity.GameSession;

/** Committed award facts and durable discovery. No network/session/eligibility authority cache. */
@Repository @Profile("mysql")
public class AwardRepository {
    public record Player(long userId,String displayName,int score,long correctTime,Integer finalRank) {}
    public record Counts(int snapshot,int opened,int scored,int uncaptured) {}
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public AwardRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public void seed(GameSession game,long at){
        var p=game.getAwardPolicySnapshot();if(p==null)return;
        jdbc.update("insert into game_award_decision(game_session_id,policy_version,policy_hash,result_revision,created_at_ms) select ?,?,?,?,? where not exists(select 1 from game_award_decision where game_session_id=?)",
                game.getId(),p.policyId(),p.hash(),game.getRevision(),at,game.getId());
    }
    public List<Long> pending(){return jdbc.queryForList("select g.id from game_session g left join game_award_decision d on d.game_session_id=g.id where g.status='FINISHED' and g.award_policy_snapshot is not null and (d.game_session_id is null or (d.state='PENDING' and d.retry_after_ms<=?)) order by g.id limit 32",Long.class,System.currentTimeMillis());}
    public boolean exists(long game){return jdbc.queryForObject("select count(*) from game_award_decision where game_session_id=?",Long.class,game)>0;}
    public boolean terminal(GameSession game){
        var row=jdbc.queryForMap("select state,policy_version,policy_hash,result_revision from game_award_decision where game_session_id=? for update",game.getId());
        if(!row.get("state").equals("PENDING"))return true;
        var policy=game.getAwardPolicySnapshot();
        if(!policy.policyId().equals(row.get("policy_version")) || !policy.hash().equals(row.get("policy_hash"))
                || ((Number)row.get("result_revision")).longValue()!=game.getRevision())throw new IllegalStateException("AWARD_EVIDENCE_MISMATCH");
        return false;
    }
    public List<Player> players(long game){return jdbc.query("select p.user_id,m.display_name_snapshot,p.score,p.total_correct_answer_time_ms,p.final_rank from player_session p join game_member m on m.game_session_id=p.game_session_id and m.user_id=p.user_id where p.game_session_id=? order by p.user_id",
            (r,n)->new Player(r.getLong(1),r.getString(2),r.getInt(3),r.getLong(4),r.getObject(5,Integer.class)),game);}
    public Counts counts(long game){return jdbc.queryForObject("select count(*),coalesce(sum(opened_at_ms is not null),0),coalesce(sum(scored_at_ms is not null),0),coalesce(sum(opened_at_ms is not null and participation_captured=0),0) from game_question where game_session_id=?",
            (r,n)->new Counts(r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4)),game);}
    public Map<Long,List<AwardEvaluator.Metric>> metrics(long game){
        var result=new LinkedHashMap<Long,List<AwardEvaluator.Metric>>();
        jdbc.query("select p.user_id,q.mode,count(*) d,sum(case when a.answer_status='CORRECT' then 1 else 0 end) c,sum(case when a.answer_status='CORRECT' then a.answer_time_ms else 0 end) t from game_question_participation qp join player_session p on p.id=qp.player_session_id join game_question q on q.id=qp.game_question_id left join answer a on a.player_session_id=qp.player_session_id and a.game_question_id=qp.game_question_id where qp.game_session_id=? group by p.user_id,q.mode order by p.user_id,q.mode",r->{
            result.computeIfAbsent(r.getLong("user_id"),k->new ArrayList<>()).add(new AwardEvaluator.Metric(r.getString("mode"),r.getInt("d"),r.getInt("c"),r.getLong("t")));},game);
        return result;
    }
    public void requireComplete(long game,int configured,List<Player> players,Map<Long,List<AwardEvaluator.Metric>> metrics,Counts counts){
        if(counts.snapshot()!=configured || counts.opened()!=configured || counts.scored()!=configured || counts.uncaptured()!=0
                || jdbc.queryForObject("select count(*) from game_member where game_session_id=? and participation='PLAYER'",Integer.class,game)!=players.size())incomplete();
        if(jdbc.queryForObject("select count(*) from game_question_participation qp left join answer a on a.player_session_id=qp.player_session_id and a.game_question_id=qp.game_question_id where qp.game_session_id=? and (a.id is null or a.answer_status='ACCEPTED_UNSCORED' or a.scored_at_ms is null)",Long.class,game)>0)incomplete();
        if(jdbc.queryForObject("select count(*) from answer a left join game_question_participation qp on qp.player_session_id=a.player_session_id and qp.game_question_id=a.game_question_id where a.game_session_id=? and (qp.game_question_id is null or a.answer_status='ACCEPTED_UNSCORED' or a.scored_at_ms is null)",Long.class,game)>0)incomplete();
        var ranked=new vn.edu.multigame.game.engine.RankingEngine().calculate(players.stream().map(p->new vn.edu.multigame.game.engine.RankingEngine.Entry(p.userId(),p.score(),p.correctTime())).toList());
        for(var p:players){
            if(p.finalRank()==null || ranked.stream().filter(r->r.player().userId()==p.userId()).findFirst().orElseThrow().rank()!=p.finalRank())incomplete();
            long t=metrics.getOrDefault(p.userId(),List.of()).stream().mapToLong(AwardEvaluator.Metric::correctTimeMs).sum();
            var last=jdbc.queryForList("select a.score_after from answer a join game_question q on q.id=a.game_question_id join player_session p on p.id=a.player_session_id where a.game_session_id=? and p.user_id=? order by q.order_index desc limit 1",Integer.class,game,p.userId());
            if(t!=p.correctTime() || p.score()!=(last.isEmpty()?0:last.getFirst()))incomplete();
        }
    }
    private void incomplete(){throw new IllegalStateException("AWARD_DATA_INCOMPLETE");}
    public String encode(Object value){try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    public void retry(long game){jdbc.update("update game_award_decision set attempts=attempts+1,retry_after_ms=?,last_error_code='FINALIZATION_RETRY' where game_session_id=? and state='PENDING'",System.currentTimeMillis()+2000,game);}
}
