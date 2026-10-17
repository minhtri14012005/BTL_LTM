package vn.edu.multigame.achievements.controller;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.achievements.dto.GameAwardsView;
import vn.edu.multigame.achievements.model.ApprovedAwardPolicy;
import vn.edu.multigame.achievements.service.AwardReadService;
import vn.edu.multigame.auth.service.AuthService;

@RestController @Profile("mysql")
public class GameAwardsController {
    private final AuthService users;private final AwardReadService awards;
    public GameAwardsController(AuthService users,AwardReadService awards){this.users=users;this.awards=awards;}
    @GetMapping("/api/games/{gameId}/awards") public ResponseEntity<GameAwardsView> read(Authentication auth,@PathVariable long gameId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(awards.read(users.requireActiveUser(auth).getId(),gameId));
    }
    @GetMapping("/api/awards/policy") public ResponseEntity<Map<String,Object>> policy(Authentication auth){
        users.requireActiveUser(auth);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("availability","ACTIVE","policy",ApprovedAwardPolicy.active(),"serverTimeMs",System.currentTimeMillis()));
    }
}
