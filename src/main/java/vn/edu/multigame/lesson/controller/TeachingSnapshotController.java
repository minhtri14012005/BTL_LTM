package vn.edu.multigame.lesson.controller;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.lesson.dto.LessonRealtimeView;
import vn.edu.multigame.lesson.service.LessonRealtimeTransactions;
@RestController @Profile("mysql")
public class TeachingSnapshotController {
    private final LessonRealtimeTransactions transactions;
    public TeachingSnapshotController(LessonRealtimeTransactions transactions){this.transactions=transactions;}
    @GetMapping("/api/teaching/lessons/{id}/snapshot")
    public LessonRealtimeView get(Authentication caller,@PathVariable long id){return transactions.snapshot(caller,id);}
}
