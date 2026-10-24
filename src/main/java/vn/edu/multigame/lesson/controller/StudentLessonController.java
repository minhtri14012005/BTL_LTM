package vn.edu.multigame.lesson.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import vn.edu.multigame.lesson.dto.LessonRealtimeView;
import vn.edu.multigame.lesson.service.LessonRealtimeTransactions;

/** Student snapshots and images use exactly the same admission and visibility guards as WS. */
@RestController @Profile("mysql") @RequestMapping("/api/student/lessons/{id}")
public class StudentLessonController {
    private final LessonRealtimeTransactions transactions;
    public StudentLessonController(LessonRealtimeTransactions transactions){this.transactions=transactions;}
    @GetMapping("/snapshot") public ResponseEntity<LessonRealtimeView> snapshot(Authentication caller,@PathVariable long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(transactions.snapshot(caller,id));}
    @GetMapping("/games/{position}/snapshot") public ResponseEntity<LessonRealtimeView> gameSnapshot(Authentication caller,@PathVariable long id,@PathVariable int position){
        var snapshot=transactions.snapshot(caller,id);
        if(snapshot.lesson().games().stream().noneMatch(g->g.position()==position))throw new vn.edu.multigame.lesson.service.LessonFailure(HttpStatus.NOT_FOUND,"GAME_NOT_FOUND");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot);
    }
    @GetMapping("/images/{hash}") public ResponseEntity<byte[]> image(Authentication caller,@PathVariable long id,@PathVariable String hash,@RequestParam long token){
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(transactions.image(caller,id,hash,token));
    }
}
