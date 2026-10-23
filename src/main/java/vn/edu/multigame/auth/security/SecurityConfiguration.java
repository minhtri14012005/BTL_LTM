package vn.edu.multigame.auth.security;

import vn.edu.multigame.common.config.WebAccessProperties;

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
            ObjectProvider<SecurityContextRepository> contexts, ObjectProvider<CsrfTokenRepository> csrf,
            ObjectProvider<vn.edu.multigame.auth.service.AuthService> users,ObjectProvider<AuthSessionRegistry> sessions) throws Exception {
        boolean mysql = environment.acceptsProfiles(Profiles.of("mysql"));
        http.cors(corsConfig -> {});
        if (mysql) {
            http.addFilterAfter(new AccountRequestFilter(users.getObject(),sessions.getObject(),errors),org.springframework.security.web.context.SecurityContextHolderFilter.class);
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
            access.requestMatchers(HttpMethod.GET, "/", "/index.html", "/login", "/app", "/app/**", "/assets/**",
                    "/api/system/status", "/api/system/ready").permitAll().requestMatchers("/error").permitAll();
            if (mysql) {
                access.requestMatchers(HttpMethod.POST,"/api/rooms","/api/rooms/*/start","/api/rooms/*/open","/api/games/*/cancel").denyAll();
                access.requestMatchers(HttpMethod.GET,"/api/learning/**").authenticated();
                access.requestMatchers("/api/teaching/classes/*/rank-config","/api/teaching/classes/*/rank-config/**").hasRole("GIANG_VIEN");
                access.requestMatchers("/api/admin/academic/**").hasRole("ADMIN");
                access.requestMatchers("/api/teaching/classes/*/wheel", "/api/teaching/lessons/**","/api/teaching/classes/*/lessons","/api/teaching/classes/*/lesson-progress").hasRole("GIANG_VIEN");
                access.requestMatchers("/api/student/lessons/**").hasRole("SINH_VIEN");
                access.requestMatchers("/api/student/classes","/api/student/classes/**").hasRole("SINH_VIEN");
                access.requestMatchers("/api/teaching/classes/*/room","/api/teaching/classes/*/members","/api/teaching/classes/*/members/**").hasRole("GIANG_VIEN");
                access.requestMatchers(HttpMethod.GET,"/api/teaching/classes","/api/teaching/classes/*").hasRole("GIANG_VIEN");
                access.requestMatchers(HttpMethod.POST,"/api/admin/teachers","/api/admin/users/*/expel","/api/admin/users/*/restore","/api/admin/games/*/kick","/api/admin/users/*/temporary-ban","/api/admin/users/*/ban","/api/admin/users/*/unban","/api/admin/rooms/*/close-orphan").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/admin/**","/admin","/admin/**","/ws/admin").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**","/admin","/admin/**","/ws/admin").denyAll();
                access.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/ws").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/auth/keepalive").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/profile").authenticated();
                access.requestMatchers(HttpMethod.GET,"/api/quizzes","/api/quizzes/*","/api/quizzes/*/preview").hasRole("GIANG_VIEN")
                        .requestMatchers(HttpMethod.GET, "/api/quizzes/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/quizzes", "/api/quizzes/*/images", "/api/quizzes/images/drafts", "/api/quizzes/*/videos", "/api/quizzes/videos/drafts").hasRole("GIANG_VIEN")
                        .requestMatchers(HttpMethod.PUT, "/api/quizzes/*").hasRole("GIANG_VIEN")
                        .requestMatchers(HttpMethod.DELETE, "/api/quizzes/*").hasRole("GIANG_VIEN");
                access.requestMatchers(HttpMethod.GET, "/api/rooms", "/api/rooms/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/rooms", "/api/rooms/*/open", "/api/rooms/*/close", "/api/rooms/*/start").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/rooms/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/*").authenticated();
                access.requestMatchers(HttpMethod.GET,"/api/games/*/snapshot").authenticated();
                access.requestMatchers(HttpMethod.GET,"/api/games/*/awards","/api/awards/policy").authenticated();
                access.requestMatchers(HttpMethod.GET,"/api/profiles/me","/api/profiles/me/awards","/api/profiles/*","/api/leaderboard").authenticated();
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
