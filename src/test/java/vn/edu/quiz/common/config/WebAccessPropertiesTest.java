package vn.edu.quiz.common.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebAccessPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = { "*", "http://*.example.com", "http://localhost:8080/", "http://localhost:8080/path",
            "http://user:password@localhost:8080", "http://localhost:8080?token=x", "file:///tmp", "null" })
    void rejectsOriginsThatCannotBeSafelyAllowed(String origin) {
        assertThatThrownBy(() -> new WebAccessProperties(List.of(origin)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsExplicitLocalAndLanOrigins() {
        assertThat(new WebAccessProperties(List.of("http://localhost:8080", "http://192.168.1.10:8080",
                "https://quiz.example.com")).allowedOrigins()).hasSize(3);
    }

    @Test
    void rejectsEmptyAllowlist() {
        assertThatThrownBy(() -> new WebAccessProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
