package vn.edu.multigame.system.controller;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

final class CleanupMigrationChecks {
    private static final Set<String> LEGACY = Set.of("room", "room_member", "room_stage", "game_session",
            "game_member", "player_session", "game_stage", "game_question", "answer",
            "game_question_participation", "game_award_decision", "game_award_participant",
            "user_active_game", "game_decision_action", "game_participation_restriction",
            "admin_kick_action", "admin_orphan_close_action");

    static void verifyCleanSchema(JdbcTemplate jdbc) {
        ArchitectureSchemaChecks.structure(jdbc);
        var tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'", String.class);
        assertThat(tables).hasSize(38).doesNotContainAnyElementsOf(LEGACY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE mode NOT IN ('QUIZ','FILL_BLANK','ASSIGNMENT')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE mode NOT IN ('QUIZ','FILL_BLANK','ASSIGNMENT')", Integer.class)).isZero();
        var dependencies = jdbc.queryForList("SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL");
        for (var fk : dependencies) {
            assertThat(LEGACY).doesNotContain(fk.get("REFERENCED_TABLE_NAME").toString());
            String table=fk.get("TABLE_NAME").toString(), column=fk.get("COLUMN_NAME").toString();
            String parent=fk.get("REFERENCED_TABLE_NAME").toString(), key=fk.get("REFERENCED_COLUMN_NAME").toString();
            // Composite FKs are also checked by MySQL itself; every non-null component must resolve.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"` c LEFT JOIN `"+parent+"` p ON c.`"+column+"`=p.`"+key+"` WHERE c.`"+column+"` IS NOT NULL AND p.`"+key+"` IS NULL",Long.class)).as(table+"."+column).isZero();
        }
        for (String body : jdbc.queryForList("SELECT ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()",String.class)) {
            for (String old : LEGACY) assertThat(body).doesNotMatch("(?is).*\\b"+old+"\\b.*");
        }
    }

    static void verifyFreshChain(String rootUrl,String user,String password) throws Exception {
        String schema="multigame_test_"+UUID.randomUUID().toString().replace("-", "");
        String url=rootUrl.replace("/?", "/"+schema+"?");
        try(var owner=DriverManager.getConnection(rootUrl,user,password);var sql=owner.createStatement()) {
            sql.executeUpdate("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try {
                Flyway.configure().dataSource(url,user,password).load().migrate();
                verifyCleanSchema(new JdbcTemplate(new DriverManagerDataSource(url,user,password)));
            } finally {clearOwnedSchema(url,user,password,schema);}
        }
    }

    static void clearOwnedSchema(String url,String user,String password,String schema) throws Exception {
        if(!schema.matches("multigame_test_[0-9a-f]{32}"))throw new IllegalStateException("Not an owned test schema");
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            if(!schema.equals(connection.getCatalog()))throw new IllegalStateException("Test catalog mismatch");
            try(var result=sql.executeQuery("SELECT @@datadir")) {
                result.next();
                if(!Path.of(result.getString(1)).toAbsolutePath().normalize().startsWith(Path.of(".cache").toAbsolutePath().normalize()))throw new IllegalStateException("Test cleanup requires isolated MySQL");
            }
            var tables=new LinkedHashSet<String>();
            try(var result=sql.executeQuery("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")){while(result.next())tables.add(result.getString(1));}
            var parents=new HashMap<String,Set<String>>();
            try(var result=sql.executeQuery("SELECT TABLE_NAME,REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL")) {
                while(result.next()) if(!result.getString(1).equals(result.getString(2)))parents.computeIfAbsent(result.getString(1),k->new HashSet<>()).add(result.getString(2));
            }
            while(!tables.isEmpty()) {
                String child=tables.stream().filter(t->tables.stream().noneMatch(other->!other.equals(t)&&parents.getOrDefault(other,Set.of()).contains(t))).findFirst().orElseThrow(()->new IllegalStateException("Cyclic test schema; refusing forced cleanup"));
                sql.executeUpdate("DROP TABLE `"+child+"`");tables.remove(child);
            }
        }
    }
}
