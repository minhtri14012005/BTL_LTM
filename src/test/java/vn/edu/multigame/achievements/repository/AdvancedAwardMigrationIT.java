package vn.edu.multigame.achievements.repository;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import vn.edu.multigame.achievements.model.ApprovedAwardPolicy;
import vn.edu.multigame.game.dto.MultimodeRulesSnapshot;
import static org.assertj.core.api.Assertions.*;

/** Minimal pre-existing SQL rows test migration preservation, not a fabricated playable Game. */
class AdvancedAwardMigrationIT {
    String setting(Properties p,String k,String fallback){return System.getenv().getOrDefault(k,p.getProperty(k,fallback));}
    long insert(JdbcTemplate jdbc,String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);return key.getKey().longValue();}
    long seed(JdbcTemplate jdbc,long room,long user,ApprovedAwardPolicy policy)throws Exception{
        var json=new ObjectMapper();long game=insert(jdbc,"insert into game_session(room_id,status,phase,end_reason,question_count,current_question_index,started_at_ms,finished_at_ms,schema_version,rules_version,v2_config_snapshot,award_policy_snapshot,revision) values(?,'FINISHED','FINISHED','COMPLETED',12,12,1000,2000,2,2,?,?,1)",room,json.writeValueAsString(MultimodeRulesSnapshot.forGame(12,0)),json.writeValueAsString(policy));
        jdbc.update("insert into game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) values(?,?,?,'HOST','PLAYER','Retained')",game,room,user);
        jdbc.update("insert into player_session(game_session_id,user_id,schema_version,player_state,score,remaining_spins,remaining_spin_pool,final_rank,total_correct_answer_time_ms) values(?,?,2,'PLAYING',120,0,'[]',1,1000)",game,user);
        jdbc.update("insert into game_award_decision(game_session_id,policy_version,policy_hash,result_revision,created_at_ms) values(?,?,?,1,1000)",game,policy.policyId(),policy.hash());
        jdbc.update("insert into game_award_participant(game_session_id,user_id,eligible,private_evidence,history_rank,award_rank,official_game_delta,official_win_delta,participated_count,correct_count,correct_time_ms,mode_metrics) values(?,?,1,'{}',1,1,1,1,12,10,1000,'[]')",game,user);return game;
    }
    void terminal(JdbcTemplate jdbc,long game){jdbc.update("update game_award_decision set state='DECIDED',revision=1,decided_at_ms=2000,eligibility_evaluated_at_ms=2000,reason_codes='[]',initial_player_count=3,eligible_player_count=2,configured_count=12,opened_count=12,scored_count=12,eligible_correct_count=10 where game_session_id=?",game);}
    @Test void v12UpgradePreservesOldLedgerAndOnlyNewPolicyCanInsertAdvancedEvidence()throws Exception{
        var config=new Properties();try(var r=Files.newBufferedReader(Path.of("config/application-local.properties"))){config.load(r);}
        String schema="quizz_t13_migration_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String root="jdbc:mysql://"+setting(config,"DB_HOST","127.0.0.1")+":"+setting(config,"DB_PORT","3306")+"/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        String user=setting(config,"DB_USER","quiz_app"),password=setting(config,"DB_PASSWORD","");
        new JdbcTemplate(new DriverManagerDataSource(root,user,password)).execute("create database "+schema+" character set utf8mb4 collate utf8mb4_0900_ai_ci");
        var ds=new DriverManagerDataSource(root.replace("/?","/"+schema+"?"),user,password);var jdbc=new JdbcTemplate(ds);Flyway.configure().dataSource(ds).target("12").load().migrate();
        long u=insert(jdbc,"insert into app_user(username,password_hash,display_name,created_at_ms) values('retained','test-only-credential-marker','Retained',1000)");
        long quiz=insert(jdbc,"insert into quiz(owner_user_id,title,visibility,created_at_ms) values(?,'Retained','PUBLIC',1000)",u);
        long room=insert(jdbc,"insert into room(host_user_id,quiz_id,room_code,name,status,max_players,question_duration_ms,created_at_ms) values(?,?,'T13MIGRATE','Retained','WAITING',3,1000,1000)",u,quiz);
        jdbc.update("insert into room_member(room_id,user_id,participation,status,joined_at_ms) values(?,?,'PLAYER','JOINED',1000)",room,u);
        long old=seed(jdbc,room,u,ApprovedAwardPolicy.approved());jdbc.update("insert into achievement_award(game_session_id,user_id,award_type,mode_key,earned_at_ms) values(?,?,'CUP','ALL',2000)",old,u);terminal(jdbc,old);
        var games=jdbc.queryForList("select * from game_session");var decisions=jdbc.queryForList("select * from game_award_decision");var participants=jdbc.queryForList("select * from game_award_participant");
        var ledger=jdbc.queryForList("select id,game_session_id,user_id,award_type,mode_key,award_value,earned_at_ms from achievement_award");
        var flyway=Flyway.configure().dataSource(ds).target("13").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("select * from game_session")).isEqualTo(games);assertThat(jdbc.queryForList("select * from game_award_decision")).isEqualTo(decisions);assertThat(jdbc.queryForList("select * from game_award_participant")).isEqualTo(participants);assertThat(jdbc.queryForList("select id,game_session_id,user_id,award_type,mode_key,award_value,earned_at_ms from achievement_award")).isEqualTo(ledger);
        assertThat(jdbc.queryForObject("select evidence_snapshot from achievement_award",String.class)).isNull();
        long modern=seed(jdbc,room,u,ApprovedAwardPolicy.active());long legacyPending=seed(jdbc,room,u,ApprovedAwardPolicy.approved());
        String sql="insert into achievement_award(game_session_id,user_id,award_type,mode_key,earned_at_ms,evidence_snapshot) values(?,?,?, ?,2000,?)";
        assertThatThrownBy(()->jdbc.update(sql,modern,u,"MULTITALENT","ALL",null)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update(sql,modern,u,"MULTITALENT","QUIZ","{}")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update(sql,legacyPending,u,"MULTITALENT","ALL","{}")).hasMessageContaining("Advanced award policy required");
        jdbc.update(sql,modern,u,"MULTITALENT","ALL","{}");jdbc.update(sql,modern,u,"COMEBACK","ALL","{}");
        assertThatThrownBy(()->jdbc.update(sql,modern,u,"COMEBACK","ALL","{}")).isInstanceOf(org.springframework.dao.DataAccessException.class);terminal(jdbc,modern);
        assertThatThrownBy(()->jdbc.update(sql,modern,u,"CUP","ALL",null)).hasMessageContaining("Award decision already terminal");
        assertThatThrownBy(()->jdbc.update("update achievement_award set evidence_snapshot='{}' where game_session_id=?",old)).hasMessageContaining("Award ledger is immutable");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=? and table_name='achievement_award' and index_name='uq_achievement_award' and non_unique=0",Integer.class,schema)).isEqualTo(4);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
