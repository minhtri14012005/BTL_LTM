package vn.edu.multigame.achievements.repository;

import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import static org.assertj.core.api.Assertions.*;

class AwardMigrationIT {
    String value(Properties c,String k,String fallback){return System.getenv().getOrDefault(k,c.getProperty(k,fallback));}
    long insert(JdbcTemplate jdbc,String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);return key.getKey().longValue();}
    @Test void v11UpgradeRetainsOldHistoryWithoutRetroactivePolicyAndAddsUniqueImmutableLedger()throws Exception{
        var config=new Properties();try(var f=Files.newBufferedReader(Path.of("config/application-local.properties"))){config.load(f);}
        String schema="quizz_t11_migration_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String root="jdbc:mysql://"+value(config,"DB_HOST","127.0.0.1")+":"+value(config,"DB_PORT","3306")+"/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        String user=value(config,"DB_USER","quiz_app"),password=value(config,"DB_PASSWORD","");
        new JdbcTemplate(new DriverManagerDataSource(root,user,password)).execute("create database "+schema+" character set utf8mb4 collate utf8mb4_0900_ai_ci");
        var ds=new DriverManagerDataSource(root.replace("/?","/"+schema+"?"),user,password);var jdbc=new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).target("11").load().migrate();
        long u=insert(jdbc,"insert into app_user(username,password_hash,display_name,created_at_ms) values('retained','credential-marker','Retained',1000)");
        long q=insert(jdbc,"insert into quiz(owner_user_id,title,visibility,created_at_ms) values(?,'Retained','PUBLIC',1000)",u);
        long room=insert(jdbc,"insert into room(host_user_id,quiz_id,room_code,name,status,max_players,question_duration_ms,created_at_ms) values(?,?,'AWARDMIGRATE','Retained','WAITING',3,1000,1000)",u,q);
        String rules=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(vn.edu.multigame.game.dto.GameplayRulesSnapshot.forGame(10,1000,7000));
        long g=insert(jdbc,"insert into game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,current_question_index,config_snapshot,started_at_ms,finished_at_ms) values(?,?,?,'Retained','FINISHED','FINISHED','CANCELLED',10,0,?,1000,2000)",room,q,u,rules);
        var before=jdbc.queryForList("select id,room_id,config_snapshot,status,end_reason,finished_at_ms,schema_version from game_session");var accounts=jdbc.queryForList("select * from app_user");
        var flyway=Flyway.configure().dataSource(ds).target("12").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("select id,room_id,config_snapshot,status,end_reason,finished_at_ms,schema_version from game_session")).isEqualTo(before);assertThat(jdbc.queryForList("select * from app_user")).isEqualTo(accounts);
        assertThat(jdbc.queryForObject("select game_type from game_session where id=?",String.class,g)).isEqualTo("NORMAL");assertThat(jdbc.queryForObject("select award_policy_snapshot from game_session where id=?",String.class,g)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from game_award_decision",Long.class)).isZero();assertThat(jdbc.queryForObject("select count(*) from achievement_award",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema=? and table_name='achievement_award' and column_name='mode_key'",String.class,schema)).isEqualTo("NO");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=? and table_name='achievement_award' and index_name='uq_achievement_award' and non_unique=0",Integer.class,schema)).isEqualTo(4);
        assertThatThrownBy(()->jdbc.update("update game_session set game_type='FRIENDLY_1V1' where id=?",g)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();System.out.println("T11_MIGRATION: V11->V12 retained history, NULL old policy, no backfill, immutable metadata, nonnull mode unique, rerun; schema="+schema);
    }
}
