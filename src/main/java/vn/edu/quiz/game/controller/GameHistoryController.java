package vn.edu.quiz.game.controller;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.quiz.auth.service.AuthService;
import vn.edu.quiz.game.service.GameHistoryService;
import vn.edu.quiz.game.dto.response.*;
@RestController @Profile("mysql") @RequestMapping("/api/games/history")
public class GameHistoryController {
    private final AuthService users; private final GameHistoryService history;
    public GameHistoryController(AuthService users,GameHistoryService history) {this.users=users;this.history=history;}
    @GetMapping public ResponseEntity<GameHistoryPage> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.list(users.requireActiveUser(auth).getId(),page,size));
    }
    @GetMapping("/{gameId}") public ResponseEntity<GameHistoryDetail> detail(Authentication auth,@PathVariable long gameId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.detail(users.requireActiveUser(auth).getId(),gameId));
    }
}
