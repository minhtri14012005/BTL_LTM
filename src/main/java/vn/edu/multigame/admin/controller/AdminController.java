package vn.edu.multigame.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.admin.dto.AdminPage;
import vn.edu.multigame.admin.service.AdminReadService;

@RestController @Profile("mysql")
public class AdminController {
    private final AdminReadService reads;
    public AdminController(AdminReadService reads){this.reads=reads;}
    private String session(HttpServletRequest request){var session=request.getSession(false);return session==null?null:session.getId();}
    @GetMapping(value={"/admin","/admin/"},produces=MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> entry(Authentication auth,HttpServletRequest request){reads.requireAccess(auth,session(request));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ClassPathResource("static/index.html"));}
    @GetMapping("/api/admin/users")
    ResponseEntity<AdminPage<AdminPage.User>> users(Authentication auth,HttpServletRequest request,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) vn.edu.multigame.user.enums.UserRole role,@RequestParam(defaultValue="") String q,@RequestParam(required=false) String status){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.users(auth,session(request),page,size,role,q,status));}
}
