package vn.edu.quiz.auth.security;

import vn.edu.quiz.common.config.WebAccessProperties;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
@EnableConfigurationProperties(WebAccessProperties.class)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment, AuthErrorWriter errors,
            ObjectProvider<SecurityContextRepository> contexts, ObjectProvider<CsrfTokenRepository> csrf) throws Exception {
        boolean mysql = environment.acceptsProfiles(Profiles.of("mysql"));
        http.cors(corsConfig -> {});
        if (mysql) {
            http.securityContext(context -> context.securityContextRepository(contexts.getObject()).requireExplicitSave(true))
                    .csrf(config -> config.csrfTokenRepository(csrf.getObject()))
                    .requestCache(cache -> cache.disable())
                    .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                    .exceptionHandling(handler -> handler
                            .authenticationEntryPoint((request, response, exception) -> errors.write(response, 401, "UNAUTHENTICATED", "Cần đăng nhập lại."))
                            .accessDeniedHandler((request, response, exception) -> errors.write(response, 403,
                                    exception instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN", "Request không được phép.")));
        }
        http.authorizeHttpRequests(access -> {
            access.requestMatchers(HttpMethod.GET, "/", "/index.html", "/styles.css", "/app.js", "/client/*.js",
                    "/api/system/status", "/api/system/ready").permitAll().requestMatchers("/error").permitAll();
            if (mysql) {
                access.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/ws").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated();
                access.requestMatchers(HttpMethod.GET, "/api/quizzes", "/api/quizzes/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/quizzes", "/api/quizzes/*/images").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/quizzes/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/quizzes/*").authenticated();
                access.requestMatchers(HttpMethod.GET, "/api/rooms", "/api/rooms/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/rooms", "/api/rooms/*/open", "/api/rooms/*/close", "/api/rooms/*/start").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/rooms/*").authenticated();
                access.requestMatchers(HttpMethod.GET,"/api/games/*/snapshot").authenticated();
                access.requestMatchers(HttpMethod.GET,"/api/games/history","/api/games/history/*").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/games/*/cancel").authenticated();
            }
            access.anyRequest().denyAll();
        });
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(WebAccessProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
