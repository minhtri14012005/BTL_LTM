package vn.edu.multigame.admin.controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.admin.dto.*;
import vn.edu.multigame.admin.service.AdminKickService;

@RestController @Profile("mysql")
public class AdminKickController {
    private final AdminKickService kicks;
    public AdminKickController(AdminKickService kicks){this.kicks=kicks;}
    @PostMapping("/api/admin/games/{gameId}/kick")
    CompletableFuture<ResponseEntity<KickResponse>> kick(Authentication auth,HttpServletRequest request,@PathVariable long gameId,@Valid @RequestBody KickRequest input){
        var session=request.getSession(false);
        return kicks.kick(auth,session==null?null:session.getId(),gameId,input).thenApply(r->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r));
    }
}
