package vn.edu.multigame.auth.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.auth.dto.request.*;
import vn.edu.multigame.auth.dto.response.*;
import vn.edu.multigame.auth.security.*;
import vn.edu.multigame.auth.service.*;

@RestController
@RequestMapping("/api/auth")
@Profile("mysql")
public class AuthController {
    private final AuthService users;
    private final AuthenticationManager manager;
    private final SessionAuthenticationStrategy strategy;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final AuthSessionRegistry sessions;
    public AuthController(AuthService users, AuthenticationManager manager, SessionAuthenticationStrategy strategy,
            SecurityContextRepository contexts, CsrfTokenRepository csrf, AuthSessionRegistry sessions) {
        this.users = users; this.manager = manager; this.strategy = strategy;
        this.contexts = contexts; this.csrf = csrf; this.sessions = sessions;
    }
    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName()));
    }
    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(users.register(request));
    }
    @PostMapping("/login")
    ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest body, Authentication existing,
            HttpServletRequest request, HttpServletResponse response) {
        if (existing != null && existing.isAuthenticated() && existing.getPrincipal() instanceof AuthPrincipal) {
            throw new AuthFailure(HttpStatus.CONFLICT, "ALREADY_AUTHENTICATED", "Đăng xuất trước khi đăng nhập lại.");
        }
        AuthService.validatePassword(body.password());
        Authentication authenticated = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        UserResponse user = UserResponse.from(users.requireActiveUser(authenticated));
        strategy.onAuthentication(authenticated, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated); SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        sessions.register(request.getSession(), user.id());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
    }
    @GetMapping("/me")
    ResponseEntity<UserResponse> me(Authentication authentication, HttpServletRequest request) {
        var user = users.requireActiveUser(authentication);
        var session = request.getSession(false);
        if (session == null) throw AuthService.unauthorized();
        sessions.requireValid(session.getId(), user.getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(UserResponse.from(user));
    }
    @PostMapping("/logout")
    ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) sessions.revoke(session.getId(), "LOGGED_OUT");
        var logout = new SecurityContextLogoutHandler();
        logout.setSecurityContextRepository(contexts); logout.logout(request, response, authentication);
        csrf.saveToken(null, request, response);
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("JSESSIONID", "").path(path)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").maxAge(0).build().toString());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
