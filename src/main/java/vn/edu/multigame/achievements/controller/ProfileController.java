package vn.edu.multigame.achievements.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.achievements.dto.PlayerProfileView;
import vn.edu.multigame.achievements.service.ProfileReadService;
import vn.edu.multigame.auth.service.AuthService;

@RestController @Profile("mysql")
public class ProfileController {
    private final AuthService users;
    private final ProfileReadService profiles;
    public ProfileController(AuthService users,ProfileReadService profiles) { this.users=users;this.profiles=profiles; }
    @GetMapping("/api/profiles/me")
    public ResponseEntity<PlayerProfileView> own(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.own(users.requireActiveUser(auth).getId()));
    }
    @GetMapping("/api/profiles/{userId}")
    public ResponseEntity<PlayerProfileView.PublicProfile> publicProfile(Authentication auth,@PathVariable long userId) {
        users.requireActiveUser(auth);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.publicProfile(userId));
    }
    @GetMapping("/api/profiles/me/awards")
    public ResponseEntity<PlayerProfileView.AwardPage> awards(Authentication auth,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.ownAwards(users.requireActiveUser(auth).getId(),page,size));
    }
}
