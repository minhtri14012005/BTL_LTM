package vn.edu.multigame.lesson.controller;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import vn.edu.multigame.lesson.repository.AssignmentStore;
import vn.edu.multigame.lesson.service.AssignmentHistoryService;
@RestController @Profile("mysql")
public class AssignmentController {
 private final AssignmentHistoryService history;
 public AssignmentController(AssignmentHistoryService history){this.history=history;}
 @GetMapping({"/api/student/lessons/{lesson}/games/{position}/submissions","/api/teaching/lessons/{lesson}/games/{position}/submissions"})
 public List<AssignmentStore.Submission> submissions(Authentication caller,@PathVariable long lesson,@PathVariable int position,@RequestParam(required=false) Long studentId,@RequestParam(defaultValue="0") long afterId,@RequestParam(defaultValue="50") int limit){return history.history(caller,lesson,position,studentId,afterId,limit);}
}
