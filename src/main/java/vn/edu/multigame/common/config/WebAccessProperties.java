package vn.edu.multigame.common.config;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quiz.web")
public record WebAccessProperties(List<String> allowedOrigins) {
    public WebAccessProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one explicit HTTP(S) origin is required");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                    || uri.getHost() == null || origin.contains("*")
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("Allowed origins must be explicit HTTP(S) origins without paths or wildcards");
            }
        }
    }
}
