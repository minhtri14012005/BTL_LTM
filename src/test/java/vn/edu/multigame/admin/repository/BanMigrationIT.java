package vn.edu.multigame.admin.repository;

import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class BanMigrationIT {
    String value(Properties local,String key,String fallback){return Optional.ofNullable(System.getenv(key)).orElse(local.getProperty(key,fallback));}
    @Test void v9UpgradeKeepsCredentialsRolesAndKickTablesAndEnforcesEpochAndIntentShape()throws Exception{
        var local=new Properties();try(var file=Files.newBufferedReader(Path.of("config/application-local.properties"))){local.load(file);}
        String host=value(local,"DB_HOST","127.0.0.1"),port=value(local,"DB_PORT","3306"),user=value(local,"DB_USER","quiz_app"),password=value(local,"DB_PASSWORD","");
        String schema="quizz_t09_migration_"+UUID.randomUUID().toString().replace("-","").substring(0,12),root="jdbc:mysql://"+host+":"+port+"/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        new JdbcTemplate(new DriverManagerDataSource(root,user,password)).execute("create database "+schema+" character set utf8mb4 collate utf8mb4_0900_ai_ci");
        var ds=new DriverManagerDataSource(root.replace("/?","/"+schema+"?"),user,password);var jdbc=new JdbcTemplate(ds);Flyway.configure().dataSource(ds).target("9").load().migrate();
        jdbc.update("insert into app_user(username,password_hash,display_name,created_at_ms,role) values('retained-admin','credential-marker-not-a-password','Admin',1000,'ADMIN'),('retained-user','another-credential-marker','Player',1000,'USER')");
        var before=jdbc.queryForList("select id,username,password_hash,display_name,created_at_ms,role from app_user order by id");
        var flyway=Flyway.configure().dataSource(ds).target("11").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForList("select id,username,password_hash,display_name,created_at_ms,role from app_user order by id")).isEqualTo(before);
        assertThat(jdbc.queryForList("select ban_kind from app_user",String.class)).containsOnly("NONE");assertThat(jdbc.queryForList("select auth_epoch from app_user",Long.class)).containsOnly(0L);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('game_participation_restriction','game_question_participation','game_decision_action','admin_kick_action')",Integer.class,schema)).isEqualTo(4);
        assertThatThrownBy(()->jdbc.update("update app_user set ban_kind='TEMP',banned_until_ms=null where username='retained-user'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update app_user set auth_epoch=-1 where username='retained-user'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("update app_user set ban_kind='TEMP',banned_until_ms=2000,ban_version=1,auth_epoch=1 where username='retained-user'");
        assertThat(jdbc.queryForObject("select auth_epoch from app_user where username='retained-user'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.referential_constraints where constraint_schema=? and table_name='account_effect_intent' and referenced_table_name in ('room','game_session','room_member')",Integer.class,schema)).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();System.out.println("T09_MIGRATION: V9->V11 safe defaults/credentials retained/constraints/outbox lock order/rerun; schema="+schema);
    }
}
