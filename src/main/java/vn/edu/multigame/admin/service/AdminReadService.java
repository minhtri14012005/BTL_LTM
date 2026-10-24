package vn.edu.multigame.admin.service;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.admin.dto.AdminPage;
import vn.edu.multigame.admin.repository.AdminReadRepository;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
import vn.edu.multigame.auth.service.*;

@Service @Profile("mysql")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class AdminReadService {
    private final AuthService users;
    private final AuthSessionRegistry sessions;
    private final AdminReadRepository reads;
    public AdminReadService(AuthService users,AuthSessionRegistry sessions,AdminReadRepository reads){this.users=users;this.sessions=sessions;this.reads=reads;}
    public void requireAccess(Authentication auth,String sessionId) {
        var user=users.requireAdmin(auth);sessions.requireValid(sessionId,user.getId());
    }
    private void page(int page,int size){if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)throw new AuthFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Phân trang không hợp lệ.");}
    public AdminPage<AdminPage.User> users(Authentication auth,String sessionId,int page,int size,vn.edu.multigame.user.enums.UserRole role,String query,String status){
        requireAccess(auth,sessionId);page(page,size);
        String q=query==null?"":query.strip();
        if(q.length()>100 || status!=null && !java.util.Set.of("ACTIVE","EXPELLED").contains(status))
            throw new AuthFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Bộ lọc tài khoản không hợp lệ.");
        return reads.users(page,size,role,q,status);
    }
}
