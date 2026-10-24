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
    private final AccountAccessPolicy access;
    public AuthService(UserRepository users, PasswordEncoder passwords, AccountAccessPolicy access) {
        this.users = users; this.passwords = passwords; this.access = access;
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
        user.setRole(vn.edu.multigame.user.enums.UserRole.SINH_VIEN);
        user.setUsername(username); user.setDisplayName(request.hoTen().strip());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setCreatedAtMs(System.currentTimeMillis());
        user.setMaSinhVien(request.maSinhVien().toUpperCase(Locale.ROOT));
        return UserResponse.from(users.saveAndFlush(user));
    }
    @Transactional
    public UserResponse updateProfile(Authentication auth, vn.edu.multigame.auth.dto.request.ProfileRequest request) {
        var current = requireActiveUser(auth);
        access.requireLocked(current.getId());
        access.requireSession(current.getId(), ((AuthPrincipal)auth.getPrincipal()).authEpoch());
        if (current.getRole() != vn.edu.multigame.user.enums.UserRole.SINH_VIEN)
            throw new AuthFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "Profile sinh viên chỉ dành cho sinh viên.");
        var user = users.findLockedById(current.getId()).orElseThrow(AuthService::unauthorized);
        user.setDisplayName(request.hoTen().strip());
        user.setMaSinhVien(request.maSinhVien().toUpperCase(Locale.ROOT));
        return UserResponse.from(users.saveAndFlush(user));
    }
    public void requireCompleteProfile(UserAccount user) {
        if (user.getRole() == vn.edu.multigame.user.enums.UserRole.SINH_VIEN && user.getMaSinhVien() == null)
            throw new AuthFailure(HttpStatus.FORBIDDEN, "PROFILE_REQUIRED", "Hãy hoàn thiện họ tên và mã sinh viên.");
    }
    @Transactional(readOnly = true)
    public UserAccount requireActiveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw unauthorized();
        }
        access.requireSession(principal.userId(),principal.authEpoch());
        return users.findById(principal.userId()).filter(u -> u.getDeletedAtMs() == null)
                .orElseThrow(AuthService::unauthorized);
    }
    /** Save/register the newly authenticated session while holding the shared User fence. */
    @Transactional(timeout=5,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public UserResponse completeLogin(Authentication authentication,Runnable saveSession) {
        var principal=(AuthPrincipal)authentication.getPrincipal();
        access.lock(principal.userId());
        var user=requireActiveUser(authentication);saveSession.run();return UserResponse.from(user);
    }
    public static AuthFailure unauthorized() {
        return new AuthFailure(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Cần đăng nhập lại.");
    }
    @Transactional(readOnly = true)
    public UserAccount requireAdmin(Authentication authentication) {
        var user = requireActiveUser(authentication);
        if (user.getRole() != vn.edu.multigame.user.enums.UserRole.ADMIN)
            throw new AuthFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cần quyền ADMIN.");
        return user;
    }
}
