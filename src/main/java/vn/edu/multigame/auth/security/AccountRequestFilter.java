package vn.edu.multigame.auth.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.multigame.auth.service.*;

/** Security-chain filter, not a separately registered servlet filter. Covers every authenticated API. */
public final class AccountRequestFilter extends OncePerRequestFilter {
    private final AuthService users;
    private final AuthSessionRegistry sessions;
    private final AuthErrorWriter errors;
    public AccountRequestFilter(AuthService users,AuthSessionRegistry sessions,AuthErrorWriter errors){this.users=users;this.sessions=sessions;this.errors=errors;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/system/") || !(path.startsWith("/api/") || path.equals("/ws") || path.startsWith("/ws/") || path.equals("/admin") || path.startsWith("/admin/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        try {
            if(auth!=null && auth.getPrincipal() instanceof AuthPrincipal principal){
                var user=users.requireActiveUser(auth);
                String resource=request.getRequestURI().substring(request.getContextPath().length());
                if (!java.util.Set.of("/api/auth/me","/api/auth/profile","/api/auth/logout","/api/auth/csrf","/api/auth/login","/api/auth/keepalive").contains(resource))
                    users.requireCompleteProfile(user);
                var session=request.getSession(false);
                if(session==null)throw AuthService.unauthorized();sessions.requireValid(session.getId(),principal.userId());
            }
        }catch(AuthFailure failure){
            String path=request.getRequestURI().substring(request.getContextPath().length());
            // An invalid old cookie must not trap the shared login or prevent logout.
            // Login still authenticates credentials and rechecks authority under User lock.
            if(path.equals("/api/auth/logout")){chain.doFilter(request,response);return;}
            if(path.equals("/api/auth/csrf") || path.equals("/api/auth/login")){
                var session=request.getSession(false);if(session!=null)sessions.revoke(session.getId(),"SESSION_REVOKED");
                SecurityContextHolder.clearContext();chain.doFilter(request,response);return;
            }
            errors.write(response,failure.status().value(),failure.code(),failure.getMessage());return;
        }
        catch(org.springframework.dao.DataAccessException unavailable){errors.write(response,503,"AUTH_UNAVAILABLE","Chưa thể xác thực; thử lại sau.");return;}
        chain.doFilter(request,response);
    }
}
