package vn.edu.multigame.admin.service;

import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.admin.dto.TeacherRequest;
import vn.edu.multigame.auth.dto.response.UserResponse;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.enums.UserRole;
import vn.edu.multigame.user.repository.UserRepository;

@Service @Profile("mysql")
public class TeacherAccountService {
    private final AuthService auth;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public TeacherAccountService(AuthService auth, UserRepository users, PasswordEncoder passwords) {
        this.auth=auth; this.users=users; this.passwords=passwords;
    }
    @Transactional
    public UserResponse create(Authentication caller, TeacherRequest input) {
        auth.requireAdmin(caller);
        AuthService.validatePassword(input.password());
        var teacher=new UserAccount();
        teacher.setRole(UserRole.GIANG_VIEN);
        teacher.setUsername(input.username().toLowerCase(Locale.ROOT));
        teacher.setDisplayName(input.hoTen().strip());
        teacher.setPasswordHash(passwords.encode(input.password()));
        teacher.setCreatedAtMs(System.currentTimeMillis());
        return UserResponse.from(users.saveAndFlush(teacher));
    }
}
