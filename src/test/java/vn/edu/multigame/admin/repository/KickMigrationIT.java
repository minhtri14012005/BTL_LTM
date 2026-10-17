package vn.edu.multigame.admin.repository;

import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import vn.edu.multigame.game.dto.GameplayRulesSnapshot;
import static org.assertj.core.api.Assertions.*;

/** Actual V8 -> V9 upgrade in a private schema; existing credentials/history stay intact. */
class KickMigrationIT {
    JdbcTemplate jdbc;
    long insert(String sql,Object...values){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;},keys);return keys.getKey().longValue();}
    String value(Properties local,String key,String fallback){return Optional.ofNullable(System.getenv(key)).orElse(local.getProperty(key,fallback));}
    @Test void preservesV8HistoryAndBackfillsOnlyOpenedParticipationWithScopedForeignKeys()throws Exception{
        var local=new Properties();try(var file=Files.newBufferedReader(Path.of("config/application-local.properties"))){local.load(file);}
        String host=value(local,"DB_HOST","127.0.0.1"),port=value(local,"DB_PORT","3306"),user=value(local,"DB_USER","quiz_app"),password=value(local,"DB_PASSWORD","");
        String schema="quizz_t08_migration_"+UUID.randomUUID().toString().replace("-","").substring(0,12),root="jdbc:mysql://"+host+":"+port+"/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        new JdbcTemplate(new DriverManagerDataSource(root,user,password)).execute("create database "+schema+" character set utf8mb4 collate utf8mb4_0900_ai_ci");
        var ds=new DriverManagerDataSource(root.replace("/?","/"+schema+"?"),user,password);jdbc=new JdbcTemplate(ds);Flyway.configure().dataSource(ds).target("8").load().migrate();
        long author=insert("insert into app_user(username,password_hash,display_name,created_at_ms) values('author','noncredential-marker','Author',1000)"),player=insert("insert into app_user(username,password_hash,display_name,created_at_ms) values('player','noncredential-marker','Player',1000)");
        long quiz=insert("insert into quiz(owner_user_id,title,visibility,created_at_ms) values(?,'Legacy','PUBLIC',1000)",author);
        long source=insert("insert into question(quiz_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,created_at_ms) values(?,1,'Legacy question','A','B','C','D','D',1000)",quiz);
        long room=insert("insert into room(host_user_id,quiz_id,room_code,name,status,max_players,question_duration_ms,created_at_ms) values(?,?,'MIGRATE','Migration','ACTIVE',3,1000,1000)",author,quiz);
        jdbc.update("insert into room_member(room_id,user_id,participation,status,joined_at_ms) values(?,?,'SPECTATOR','JOINED',1000),(?,?,'PLAYER','JOINED',1000)",room,author,room,player);
        String config=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(GameplayRulesSnapshot.forGame(10,1000,7000));
        long game=insert("insert into game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,question_count,current_question_index,config_snapshot,started_at_ms,phase_opened_at_ms,phase_deadline_at_ms) values(?,?,?,'Legacy','ACTIVE','QUESTION_CLOSED',10,1,?,1000,1000,2000)",room,quiz,author,config);
        jdbc.update("insert into game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) values(?,?,?,'HOST','SPECTATOR','Host'),(?,?,?,'MEMBER','PLAYER','Player')",game,room,author,game,room,player);
        long ps=insert("insert into player_session(game_session_id,user_id,player_state,score,remaining_spins,remaining_spin_pool) values(?,?,'PLAYING',20,1,'[\"SAFE\"]')",game,player);
        long opened=insert("insert into game_question(game_session_id,source_question_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,question_duration_ms,phase,opened_at_ms,deadline_at_ms) values(?,?,1,'Opened','A','B','C','D','D',1000,'QUESTION_CLOSED',1000,2000)",game,source);
        long unopened=insert("insert into game_question(game_session_id,source_question_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,question_duration_ms,phase) values(?,?,2,'Not opened','A','B','C','D','D',1000,'DECISION')",game,source);
        jdbc.update("insert into answer(game_session_id,player_session_id,game_question_id,answer_status,selected_option,received_at_ms,answer_time_ms) values(?,?,?,'ACCEPTED_UNSCORED','D',1100,100)",game,ps,opened);
        var answerBefore=jdbc.queryForList("select * from answer");var usersBefore=jdbc.queryForList("select * from app_user");String configBefore=jdbc.queryForObject("select cast(config_snapshot as char) from game_session where id=?",String.class,game);
        var flyway=Flyway.configure().dataSource(ds).target("9").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("select * from answer")).isEqualTo(answerBefore);assertThat(jdbc.queryForList("select * from app_user")).isEqualTo(usersBefore);assertThat(jdbc.queryForObject("select cast(config_snapshot as char) from game_session where id=?",String.class,game)).isEqualTo(configBefore);
        assertThat(jdbc.queryForList("select membership_generation from room_member",Long.class)).containsOnly(1L);assertThat(jdbc.queryForList("select membership_generation from game_member",Long.class)).containsOnly(1L);
        assertThat(jdbc.queryForObject("select count(*) from game_question_participation where game_question_id=? and player_session_id=?",Long.class,opened,ps)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select participation_captured from game_question where id=?",Boolean.class,opened)).isTrue();assertThat(jdbc.queryForObject("select participation_captured from game_question where id=?",Boolean.class,unopened)).isFalse();assertThat(jdbc.queryForObject("select count(*) from game_question_participation where game_question_id=?",Long.class,unopened)).isZero();
        long other=insert("insert into game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,current_question_index,config_snapshot,started_at_ms,finished_at_ms) values(?,?,?,'Other','FINISHED','FINISHED','CANCELLED',10,0,?,1000,2000)",room,quiz,author,config);
        reject(1452,()->jdbc.update("insert into game_question_participation(game_session_id,game_question_id,player_session_id,opened_at_ms) values(?,?,?,1000)",other,unopened,ps));
        reject(3819,()->jdbc.update("update room_member set membership_generation=0 where room_id=?",room));assertThat(flyway.migrate().migrationsExecuted).isZero();
        System.out.println("T08_MIGRATION: V8 history retained, OPEN-only backfill and scoped FK; schema="+schema);
    }
    void reject(int code,Runnable operation){Throwable failure=catchThrowable(operation::run);assertThat(failure).isInstanceOf(org.springframework.dao.DataAccessException.class);while(failure.getCause()!=null)failure=failure.getCause();assertThat(failure).isInstanceOf(java.sql.SQLException.class);assertThat(((java.sql.SQLException)failure).getErrorCode()).isEqualTo(code);}
}
