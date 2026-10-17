package vn.edu.multigame.achievements.repository;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.achievements.model.StageRankEvidence;
import vn.edu.multigame.game.engine.RankingEngine;

/** Reconstructs a committed stage boundary; never rescores or manufactures a missing snapshot. */
@Repository @Profile("mysql")
public class StageRankEvidenceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public StageRankEvidenceRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private record Stage(long id,int order,int first,int count,String mode) {}
    public StageRankEvidence read(long game,List<Long> eligible) {
        var stages=jdbc.query("select id,order_index,first_question_index,question_count,mode from game_stage where game_session_id=? order by order_index",
                (r,n)->new Stage(r.getLong(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getString(5)),game);
        if(stages.isEmpty())throw incomplete();
        int next=1;
        for(int i=0;i<stages.size();i++){
            var s=stages.get(i);if(s.order()!=i+1 || s.first()!=next || s.count()<1)throw incomplete();next+=s.count();
            int actual=jdbc.queryForObject("select count(*) from game_question where game_session_id=? and stage_id=? and mode=? and order_index between ? and ? and stage_question_index=order_index-?+1",Integer.class,game,s.id(),s.mode(),s.first(),next-1,s.first());
            if(actual!=s.count())throw incomplete();
        }
        if(stages.size()<2)return new StageRankEvidence(stages.size(),0,List.of());
        var first=stages.getFirst();int end=first.first()+first.count()-1;
        var inputs=new ArrayList<RankingEngine.Entry>();
        if(new HashSet<>(eligible).size()!=eligible.size())throw incomplete();
        for(long user:eligible){
            var stats=jdbc.queryForMap("""
                select count(*) participated, count(a.scored_at_ms) scored,
                  sum(case when a.answer_status='CORRECT' then a.answer_time_ms else 0 end) correct_time,
                  sum(case when a.answer_status in ('CORRECT','WRONG','NO_ANSWER') and q.scored_at_ms is not null and q.participation_captured=1 then 1 else 0 end) valid_outcomes
                from game_question_participation qp join player_session p on p.id=qp.player_session_id
                join game_question q on q.id=qp.game_question_id
                left join answer a on a.game_question_id=q.id and a.player_session_id=p.id
                where qp.game_session_id=? and p.game_session_id=? and q.game_session_id=? and p.user_id=? and q.stage_id=?
                """,game,game,game,user,first.id());
            if(number(stats,"participated")!=first.count() || number(stats,"scored")!=first.count() || number(stats,"valid_outcomes")!=first.count())throw incomplete();
            var boundary=jdbc.queryForList("""
                select a.score_after,a.result_snapshot from answer a
                join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id
                where p.user_id=? and p.game_session_id=? and q.game_session_id=? and q.order_index=? and a.scored_at_ms is not null
                """,user,game,game,end);
            if(boundary.size()!=1 || !(boundary.getFirst().get("score_after") instanceof Number score))throw incomplete();
            long time=number(stats,"correct_time");
            try{
                var snapshot=json.readTree((String)boundary.getFirst().get("result_snapshot"));
                if(snapshot==null || !snapshot.isObject())throw incomplete();
                var total=snapshot.get("totalCorrectAnswerTimeMs");
                if(total==null || !total.isIntegralNumber() || !total.canConvertToLong() || total.longValue()<0 || total.longValue()!=time)throw incomplete();
            }catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e){throw incomplete();}
            inputs.add(new RankingEngine.Entry(user,score.intValue(),time));
        }
        var ranks=new RankingEngine().calculate(inputs).stream().map(r->new StageRankEvidence.Standing(r.player().userId(),r.player().score(),r.player().totalAnswerTimeMs(),r.rank())).toList();
        return new StageRankEvidence(stages.size(),end,ranks);
    }
    private long number(Map<String,Object> row,String name){if(!(row.get(name) instanceof Number n))throw incomplete();return n.longValue();}
    private IllegalStateException incomplete(){return new IllegalStateException("AWARD_STAGE_DATA_INCOMPLETE");}
}
