package vn.edu.multigame.system.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.multigame.MultigameApplication;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in baseline: owns schemas on an isolated MySQL instance and clears their tables after use. */
class BaselineMySqlIT {
    @Test
    void migratesValidatesAndServesHttpOnAnOwnedDatabase() throws Exception {
        var local = new Properties();
        Path config = Path.of("config/application-local.properties");
        if (Files.exists(config)) {
            try (var reader = Files.newBufferedReader(config)) { local.load(reader); }
        }
        String host = setting(local, "DB_HOST", "127.0.0.1");
        String port = setting(local, "DB_PORT", "3306");
        String user = setting(local, "DB_USER", "quiz_app");
        String password = setting(local, "DB_PASSWORD", "");
        String schema = "multigame_test_" + UUID.randomUUID().toString().replace("-", "");
        String rootUrl = "jdbc:mysql://" + host + ":" + port + "/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        String databaseUrl = rootUrl.replace("/?", "/" + schema + "?");
        Path imageRoot=Path.of(".cache","baseline-media",schema).toAbsolutePath().normalize();
        boolean created = false;
        try (var owner = DriverManager.getConnection(rootUrl, user, password);
                var statement = owner.createStatement()) {
            try {
                try(var check=statement.executeQuery("SELECT @@datadir")) {
                    check.next();var actualData=Path.of(check.getString(1)).toAbsolutePath().normalize();
                    if(!actualData.startsWith(Path.of(".cache").toAbsolutePath().normalize()))throw new IllegalStateException("Baseline tests require an isolated MySQL data directory inside the workspace .cache");
                }
                // Never reuse a pre-existing schema.
                CleanupMigrationChecks.verifyFreshChain(rootUrl,user,password);
                statement.executeUpdate("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                created = true;
                org.flywaydb.core.Flyway.configure().dataSource(databaseUrl,user,password).target("13").load().migrate();
                String legacyHash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("LegacyPass123!");
                statement.executeUpdate("INSERT INTO `"+schema+"`.app_user(username,password_hash,display_name,created_at_ms,role) VALUES('legacy','"+legacyHash+"','Legacy Student',1,'USER')");
                statement.executeUpdate("INSERT INTO `"+schema+"`.quiz(owner_user_id,title,visibility,created_at_ms) SELECT id,'Retained history','PRIVATE',1 FROM `"+schema+"`.app_user WHERE username='legacy'");
                org.flywaydb.core.Flyway.configure().dataSource(databaseUrl,user,password).target("15").load().migrate();
                try(var upgrade=DriverManager.getConnection(databaseUrl,user,password);var seed=upgrade.createStatement()) {
                    seed.executeUpdate("INSERT INTO academic_year(code,name) VALUES('UPGRADE','Existing year')");
                    seed.executeUpdate("INSERT INTO academic_semester(academic_year_id,code,name) SELECT id,'UPGRADE','Existing semester' FROM academic_year WHERE code='UPGRADE'");
                    seed.executeUpdate("INSERT INTO academic_subject(code,name) VALUES('UPGRADE','Existing subject')");
                    seed.executeUpdate("INSERT INTO subject_offering(semester_id,subject_id) SELECT s.id,u.id FROM academic_semester s CROSS JOIN academic_subject u WHERE s.code='UPGRADE' AND u.code='UPGRADE'");
                    seed.executeUpdate("INSERT INTO course_class(offering_id,code,name,required_lessons) SELECT id,'UPGRADE','Existing class',5 FROM subject_offering");
                }
                try (var context = new SpringApplicationBuilder(MultigameApplication.class).run(
                        "--spring.profiles.active=mysql,baseline", "--BASELINE_DB_URL=" + databaseUrl,
                        "--spring.datasource.url=" + databaseUrl,
                        "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                        "--quiz.images.directory="+imageRoot,
                        "--server.address=127.0.0.1", "--server.port=0",
                        "--logging.level.org.springframework=WARN", "--logging.level.org.hibernate=WARN",
                        "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.clean-disabled=true")) {
                    var logging=org.springframework.boot.logging.LoggingSystem.get(getClass().getClassLoader());
                    for(String logger:java.util.List.of("org.springframework","org.springframework.jdbc.core","org.springframework.web","org.springframework.transaction","org.hibernate","org.hibernate.SQL"))logging.setLogLevel(logger,org.springframework.boot.logging.LogLevel.WARN);
                    var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(schema);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class))
                            .isEqualTo(32);
                    CleanupMigrationChecks.verifyCleanSchema(jdbc);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(1);
                    assertThat(context.getBean(vn.edu.multigame.realtime.session.LessonRuntime.class)).isNotNull();
                    int httpPort = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
                    if (!(Boolean.getBoolean("frontend.e2e") && Boolean.getBoolean("frontend.e2e.only"))) {
                    vn.edu.multigame.auth.controller.AccountLifecycleChecks.verify(context, jdbc, httpPort);
                    vn.edu.multigame.academic.controller.AcademicLifecycleChecks.verify(context, jdbc, httpPort);
                    if(!Boolean.getBoolean("baseline.management.only")) {
                    vn.edu.multigame.room.controller.ClassMembershipChecks.verify(jdbc,httpPort);
                    vn.edu.multigame.lesson.controller.LessonLifecycleChecks.verify(context,jdbc,httpPort);
                    vn.edu.multigame.questionbank.controller.LearningBankChecks.verify(context,jdbc,httpPort);
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifyAchievements(context,jdbc,httpPort);
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifyBoards(context,jdbc,httpPort);
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifyAssignment(context,jdbc,httpPort);
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifyFillBlank(context,jdbc,httpPort);
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifyQuiz(context,jdbc,httpPort);
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifyWheel(context,jdbc,httpPort);
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifySettingsVisibility(context,jdbc,httpPort);
                    }
                    vn.edu.multigame.realtime.session.LessonRealtimeChecks.verifyManagement(context,jdbc,httpPort);
                    ManagementLifecycleChecks.verify(jdbc,httpPort);
                    if(!Boolean.getBoolean("baseline.management.only")) {
                        vn.edu.multigame.realtime.session.LessonRealtimeChecks.verify(context,jdbc,httpPort);
                        ArchitectureSchemaChecks.ownership(context,jdbc);
                    }
                    }
                    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                    for (String path : new String[] {"/", "/api/system/ready"}) {
                        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + path))
                                .timeout(Duration.ofSeconds(10)).GET().build();
                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertThat(response.statusCode()).as(path).isEqualTo(200);
                        if (path.endsWith("ready")) assertThat(response.body()).contains("\"status\":\"UP\"");
                    }
                }
                if (Boolean.getBoolean("frontend.e2e")) FrontendBrowserChecks.verifyPackaged(databaseUrl,user,password);
            } finally {
                if (created) {
                    if (!schema.matches("multigame_test_[0-9a-f]{32}")
                            || !databaseUrl.equals(rootUrl.replace("/?", "/" + schema + "?"))
                            || !owner.getMetaData().getURL().equals(rootUrl)) {
                        throw new IllegalStateException("Refusing cleanup: database ownership/URL mismatch");
                    }
                    vn.edu.multigame.system.controller.CleanupMigrationChecks.clearOwnedSchema(databaseUrl,user,password,schema);
                    if(!imageRoot.startsWith(Path.of(".cache","baseline-media").toAbsolutePath().normalize()) || !imageRoot.getFileName().toString().equals(schema))throw new IllegalStateException("Unsafe test image path");
                    if(Files.exists(imageRoot))try(var files=Files.walk(imageRoot)){for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(file);}


                }
            }
        }
    }

    private static String setting(Properties local, String name, String fallback) {
        return System.getenv().getOrDefault(name, local.getProperty(name, fallback));
    }
}
