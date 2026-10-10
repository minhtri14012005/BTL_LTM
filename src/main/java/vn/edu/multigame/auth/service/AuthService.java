package vn.edu.multigame.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.auth.dto.request.RegisterRequest;
import vn.edu.multigame.auth.dto.response.UserResponse;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.repository.UserRepository;

@Service
@Profile("mysql")
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users; this.passwords = passwords;
    }
    public static void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Mật khẩu vượt quá 72 byte UTF-8.");
        }
    }
    @Transactional
    public UserResponse register(RegisterRequest request) {
        validatePassword(request.password());
        String username = request.username().toLowerCase(Locale.ROOT);
        if (users.findByUsername(username).isPresent()) {
            throw new AuthFailure(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Tên đăng nhập đã tồn tại.");
        }
        UserAccount user = new UserAccount();
        user.setUsername(username); user.setDisplayName(request.displayName().strip());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setCreatedAtMs(System.currentTimeMillis());
        return UserResponse.from(users.saveAndFlush(user));
    }
    @Transactional(readOnly = true)
    public UserAccount requireActiveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw unauthorized();
        }
        return users.findById(principal.userId()).filter(u -> u.getDeletedAtMs() == null)
                .orElseThrow(AuthService::unauthorized);
    }
    public static AuthFailure unauthorized() {
        return new AuthFailure(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Cần đăng nhập lại.");
    }
}
