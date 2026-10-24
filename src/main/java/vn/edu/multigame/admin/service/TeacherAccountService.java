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
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.auth.service.AccountAccessPolicy accounts;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
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
    private UserAccount target(Authentication caller,long id,long version) {
        auth.requireAdmin(caller);vn.edu.multigame.auth.service.AccountAccessPolicy.State state;
        try{state=accounts.lock(id);}catch(vn.edu.multigame.auth.service.AuthFailure missing){throw new vn.edu.multigame.common.util.OperationFailure(org.springframework.http.HttpStatus.NOT_FOUND,"USER_NOT_FOUND");}
        var teacher=users.findLockedById(id).orElseThrow(AuthService::unauthorized);
        if(teacher.getRole()!=UserRole.GIANG_VIEN)throw new vn.edu.multigame.auth.service.AuthFailure(org.springframework.http.HttpStatus.FORBIDDEN,"TEACHER_REQUIRED","Chỉ được quản lý tài khoản giảng viên.");
        if(state.deletedAtMs()!=null)throw vn.edu.multigame.common.util.OperationFailure.conflict("USER_UNAVAILABLE");
        if(state.version()!=version)throw vn.edu.multigame.common.util.OperationFailure.conflict("STALE_VERSION");
        return teacher;
    }
    private void version(long id,boolean revoke) {
        var state=accounts.state(id);long epoch=revoke?Math.incrementExact(state.epoch()):state.epoch();
        jdbc.update("UPDATE app_user SET ban_version=?,auth_epoch=? WHERE id=?",Math.incrementExact(state.version()),epoch,id);
        if(revoke){accounts.fenceForCommit(id,epoch);events.publishEvent(new TeacherSessionChanged(id,epoch));}
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public UserResponse edit(Authentication caller,long id,vn.edu.multigame.admin.dto.TeacherChanges.Edit input) {
        var teacher=target(caller,id,input.expectedVersion());String username=input.username().toLowerCase(Locale.ROOT);
        boolean revoke=!username.equals(teacher.getUsername());
        teacher.setUsername(username);teacher.setDisplayName(input.hoTen().strip());users.saveAndFlush(teacher);version(id,revoke);
        return UserResponse.from(teacher);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void password(Authentication caller,long id,vn.edu.multigame.admin.dto.TeacherChanges.Password input) {
        var teacher=target(caller,id,input.expectedVersion());AuthService.validatePassword(input.password());
        teacher.setPasswordHash(passwords.encode(input.password()));users.saveAndFlush(teacher);version(id,true);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void delete(Authentication caller,long id,vn.edu.multigame.admin.dto.TeacherChanges.Delete input) {
        var teacher=target(caller,id,input.expectedVersion());
        var classes=jdbc.queryForList("SELECT course_class_id FROM teaching_assignment WHERE teacher_id=? ORDER BY course_class_id",Long.class,id);
        for(long course:classes)jdbc.queryForObject("SELECT id FROM course_class WHERE id=? FOR UPDATE",Long.class,course);
        teacher.setDeletedAtMs(Math.max(System.currentTimeMillis(),teacher.getCreatedAtMs()));users.saveAndFlush(teacher);
        jdbc.update("DELETE FROM teaching_assignment WHERE teacher_id=?",id);version(id,true);
        for(long course:classes)events.publishEvent(new vn.edu.multigame.room.service.ClassStaffingChanged(course));
    }
}
