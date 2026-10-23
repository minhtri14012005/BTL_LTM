package vn.edu.multigame.leaderboard.controller;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import vn.edu.multigame.leaderboard.service.LearningBoardService;
import vn.edu.multigame.leaderboard.dto.LearningBoard.*;
@RestController @Profile("mysql")
public class LearningBoardController {
 private final LearningBoardService boards;
 public LearningBoardController(LearningBoardService boards){this.boards=boards;}
 @GetMapping("/api/learning/classes/{course}/leaderboard") public ClassView classroom(Authentication caller,@PathVariable long course){return boards.classroom(caller,course);}
 @GetMapping("/api/learning/leaderboard") public GlobalView global(Authentication caller,@RequestParam long subjectId,@RequestParam long academicYearId){return boards.global(caller,subjectId,academicYearId);}
 @GetMapping("/api/teaching/classes/{course}/rank-config") public Config config(Authentication caller,@PathVariable long course){return boards.config(caller,course);}
 @PutMapping("/api/teaching/classes/{course}/rank-config") public Config save(Authentication caller,@PathVariable long course,@RequestBody Config input){return boards.save(caller,course,input);}
 public record Share(long revision){}
 @PostMapping("/api/teaching/classes/{course}/rank-config/share") public Map<String,String> share(Authentication caller,@PathVariable long course,@RequestBody Share input){return Map.of("code",boards.share(caller,course,input.revision()));}
 @PostMapping("/api/teaching/classes/{course}/rank-config/import") public Config copy(Authentication caller,@PathVariable long course,@RequestBody Import input){return boards.copy(caller,course,input);}
}
