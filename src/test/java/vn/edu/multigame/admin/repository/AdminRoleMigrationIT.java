package vn.edu.multigame.admin.repository;

import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** An isolated schema proves existing accounts retain credentials and become USER. */
class AdminRoleMigrationIT {
    @Test void v7AccountsRetainedAndRoleConstraintEnforced() throws Exception {
        var local=new Properties();try(var in=Files.newBufferedReader(Path.of("config/application-local.properties"))){local.load(in);}
        String host=value(local,"DB_HOST","127.0.0.1"),port=value(local,"DB_PORT","3306"),user=value(local,"DB_USER","quiz_app"),password=value(local,"DB_PASSWORD","");
        String schema="quizz_t07_roles_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String root="jdbc:mysql://"+host+":"+port+"/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        new JdbcTemplate(new DriverManagerDataSource(root,user,password)).execute("create database "+schema+" character set utf8mb4 collate utf8mb4_0900_ai_ci");
        var ds=new DriverManagerDataSource(root.replace("/?","/"+schema+"?"),user,password);var jdbc=new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).target("7").load().migrate();
        jdbc.update("insert into app_user(username,password_hash,display_name,created_at_ms,deleted_at_ms) values('existing','unchanged-hash-marker','Existing',1000,null),('deleted','unchanged-deleted-marker','Deleted',1001,2000)");
        var before=jdbc.queryForList("select id,username,password_hash,display_name,created_at_ms,deleted_at_ms from app_user order by id");
        var flyway=Flyway.configure().dataSource(ds).target("8").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("select id,username,password_hash,display_name,created_at_ms,deleted_at_ms from app_user order by id")).isEqualTo(before);
        assertThat(jdbc.queryForList("select role from app_user",String.class)).containsExactly("USER","USER");
        rejectRole(jdbc,"OWNER");rejectRole(jdbc,"admin");
        jdbc.update("update app_user set role='ADMIN' where username='existing'");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select role from app_user where username='existing'",String.class)).isEqualTo("ADMIN");
        System.out.println("T07_ROLE_MIGRATION: isolated schema="+schema+"; V7 data retained, V8 role constraint and rerun verified");
    }
    void rejectRole(JdbcTemplate jdbc,String role){
        Throwable failure=catchThrowable(()->jdbc.update("update app_user set role=? where username='existing'",role));
        assertThat(failure).isInstanceOf(org.springframework.dao.DataAccessException.class);
        while(failure.getCause()!=null)failure=failure.getCause();
        assertThat(failure).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException)failure).getErrorCode()).isEqualTo(3819);
    }
    String value(Properties p,String key,String fallback){return Optional.ofNullable(System.getenv(key)).orElse(p.getProperty(key,fallback));}
}
