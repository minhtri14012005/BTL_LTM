package vn.edu.multigame.common.config;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class LanEnvironmentPostProcessorTest {
    private final LanEnvironmentPostProcessor processor = new LanEnvironmentPostProcessor() {
        @Override protected void addLanOrigins(Set<String> origins, String port) {
            origins.add("http://192.168.1.234:" + port);
        }
    };

    private MockEnvironment configuredEnvironment() {
        return new MockEnvironment()
                .withProperty("server.port", "${SERVER_PORT:8080}")
                .withProperty("server.address", "${SERVER_ADDRESS:127.0.0.1}")
                .withProperty("quiz.web.allowed-origins", "${ALLOWED_ORIGINS:http://localhost:8080}");
    }

    @Test void dashboardStartupAllowsLanAndBindsAllInterfaces() {
        var environment = configuredEnvironment();
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
        assertThat(List.of(environment.getProperty("quiz.web.allowed-origins").split(",")))
                .contains("http://192.168.1.234:8080", "http://localhost:8080", "http://localhost:5173")
                .doesNotContain("*");
    }

    @Test void usesConfiguredServerPort() {
        var environment = configuredEnvironment().withProperty("SERVER_PORT", "9090");
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("quiz.web.allowed-origins"))
                .contains("http://192.168.1.234:9090", "http://localhost:9090")
                .doesNotContain(":8080");
    }

    @Test void explicitConfigurationKeepsPrecedence() {
        var environment = configuredEnvironment()
                .withProperty("ALLOWED_ORIGINS", "https://quiz.example.com")
                .withProperty("SERVER_ADDRESS", "127.0.0.1");
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("quiz.web.allowed-origins")).isEqualTo("https://quiz.example.com");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Configuration(proxyBeanMethods = false)
    static class StartupConfiguration {}

    @Test void springBootLoadsLanDefaultsAfterApplicationYaml() {
        var application = new SpringApplication(StartupConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        try (var context = application.run("--spring.profiles.active=web-only", "--server.port=9091",
                "--spring.main.banner-mode=off")) {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
            assertThat(environment.getProperty("quiz.web.allowed-origins"))
                    .contains("http://localhost:9091", "http://127.0.0.1:9091", "http://localhost:5173")
                    .doesNotContain("*");
        }
    }
}
