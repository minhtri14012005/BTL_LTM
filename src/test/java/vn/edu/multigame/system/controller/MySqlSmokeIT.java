package vn.edu.multigame.system.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000")
@ActiveProfiles("mysql")
class MySqlSmokeIT {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.context.ConfigurableApplicationContext context;

    @LocalServerPort
    private int port;

    private final String databaseName = "quizz_task2_test";

    @Test
    void connectsToRealMysqlAndRunsJpaQuery() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT VERSION(), DATABASE(), 1")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(result.next()).isTrue();
            assertThat(result.getString(2)).isEqualTo(databaseName);
            assertThat(result.getInt(3)).isEqualTo(1);
            System.out.println("MYSQL_SMOKE: product=MySQL, version=" + result.getString(1)
                    + ", database=" + result.getString(2));
        }
        assertThat(((Number) entityManager.createNativeQuery("SELECT 1").getSingleResult()).intValue()).isEqualTo(1);
        var entities = entityManager.getMetamodel().getEntities();
        assertThat(entities).hasSize(13).allSatisfy(entity ->
                assertThat(entity.getJavaType().getPackageName()).startsWith("vn.edu.multigame."));
        assertThat(entities).extracting(entity -> entity.getName()).contains("Quiz", "Question", "GameSession", "Room", "UserAccount");
        assertThat(context.getBeansOfType(org.springframework.data.repository.Repository.class)).hasSize(13);
        assertThat(context.getBean(vn.edu.multigame.auth.security.AccountUserDetailsService.class)).isNotNull();
        assertThat(context.getBean(vn.edu.multigame.questionbank.service.QuestionBankService.class)).isNotNull();
    }

    @Test
    void reportsDatabaseReadyThroughRealHttp() throws Exception {
        assertThat(((org.springframework.beans.factory.support.DefaultListableBeanFactory) context.getBeanFactory()).isAllowCircularReferences()).isFalse();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/system/ready"))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"", "\"server\":\"UP\"", "\"gameplay\":\"GAMEPLAY_WS\"", "\"application\":\"multigame\"");
    }
}
