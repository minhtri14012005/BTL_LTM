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
    public java.util.List<AdminPage.KickTarget> players(Authentication auth,String session,long game){requireAccess(auth,session);if(game<1)throw new AuthFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Game không hợp lệ.");return reads.players(game);}
    private void page(int page,int size){if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)throw new AuthFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Phân trang không hợp lệ.");}
    public AdminPage<AdminPage.User> users(Authentication auth,String sessionId,int page,int size){requireAccess(auth,sessionId);page(page,size);return reads.users(page,size);}
    public AdminPage<AdminPage.Game> games(Authentication auth,String sessionId,int page,int size){requireAccess(auth,sessionId);page(page,size);return reads.activeGames(page,size);}
    public AdminPage<AdminPage.OrphanRoom> orphans(Authentication auth,String sessionId,int page,int size){requireAccess(auth,sessionId);page(page,size);return reads.orphanRooms(page,size);}
}
