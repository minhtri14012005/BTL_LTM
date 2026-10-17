package vn.edu.multigame.leaderboard.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.leaderboard.dto.LeaderboardView;
import vn.edu.multigame.leaderboard.service.LeaderboardReadService;

@RestController @Profile("mysql")
public class LeaderboardController {
    private final AuthService users;private final LeaderboardReadService rankings;
    public LeaderboardController(AuthService users,LeaderboardReadService rankings) { this.users=users;this.rankings=rankings; }
    @GetMapping("/api/leaderboard")
    public ResponseEntity<LeaderboardView> read(Authentication auth,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rankings.read(users.requireActiveUser(auth).getId(),page,size));
    }
}
