package vn.edu.multigame.system.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("web-only")
class SystemWebTest {
    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    void servesClientButDoesNotClaimDatabaseReadinessWithoutMysql() throws Exception {
        HttpResponse<String> page = get("/");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("<html lang=\"vi\">", "<title>ClassQuest — Hành trình thử thách trong lớp học</title>", "id=\"root\"", "/assets/");
        for (String route : new String[]{"/login", "/app", "/app/sinh_vien", "/app/giang_vien/classes/123"}) {
            assertThat(get(route).statusCode()).as(route).isEqualTo(200);
            assertThat(get(route).body()).as(route).isEqualTo(page.body());
        }
        var references=java.util.regex.Pattern.compile("(?:src|href)=\"(/assets/[^\"]+)\"").matcher(page.body());
        int count=0;
        while(references.find()) { assertThat(get(references.group(1)).statusCode()).isEqualTo(200); count++; }
        assertThat(count).isGreaterThanOrEqualTo(2);
        assertThat(get("/assets/missing.js").statusCode()).isEqualTo(404);
        assertThat(get("/api/missing").statusCode()).isNotEqualTo(200);
        for(String retired:new String[]{"/app.js","/styles.css","/js/features/game/page.js","/styles/features/multimode.css"})assertThat(get(retired).statusCode()).as(retired).isEqualTo(403);
        assertThat(get("/client/core.js").statusCode()).isEqualTo(403);
        HttpResponse<String> status = get("/api/system/status");
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).contains("\"status\":\"NOT_CONFIGURED\"", "\"gameplay\":\"NOT_IMPLEMENTED\"");
        assertThat(get("/api/system/ready").statusCode()).isEqualTo(503);
        assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
    }

    @Test
    void rejectsForeignOriginEvenForPublicStatus() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(url("/api/system/status"))
                .header("Origin", "http://untrusted.example").GET());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void allowsOnlyExplicitConfiguredOrigin() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(url("/api/system/status"))
                .header("Origin", "http://localhost:8080").GET());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:8080");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
    }

    @Test
    void doesNotExposeGameplayOrWritesBeforeAuthenticationIsImplemented() throws Exception {
        assertThat(get("/ws").statusCode()).isEqualTo(403);
        assertThat(get("/api/rooms").statusCode()).isEqualTo(403);
        HttpResponse<String> response = send(HttpRequest.newBuilder(url("/api/system/status"))
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(url(path)).GET());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
