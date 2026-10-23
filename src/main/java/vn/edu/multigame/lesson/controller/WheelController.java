package vn.edu.multigame.lesson.controller;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.context.annotation.Profile;
import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.multigame.lesson.service.WheelService;
import vn.edu.multigame.realtime.session.LessonRuntime;
@RestController @Profile("mysql")
public class WheelController {
 public record Configuration(long revision,List<String> cells){}
 public record Action(String requestId,long revision,List<Long> pool,Integer k,Map<Long,BigDecimal> grades){}
 private final WheelService service;private final LessonRuntime runtime;private final vn.edu.multigame.auth.security.AuthSessionRegistry sessions;
 public WheelController(WheelService service,LessonRuntime runtime,vn.edu.multigame.auth.security.AuthSessionRegistry sessions){this.service=service;this.runtime=runtime;this.sessions=sessions;}
 @GetMapping("/api/teaching/classes/{course}/wheel") public JsonNode get(Authentication caller,@PathVariable long course){return service.config(caller,course);}
 @PutMapping("/api/teaching/classes/{course}/wheel") public JsonNode configure(Authentication caller,@PathVariable long course,@RequestBody Configuration body){return service.configure(caller,course,body.revision(),body.cells());}
 @PostMapping("/api/teaching/lessons/{lesson}/wheel/{action}") public JsonNode act(Authentication caller,@PathVariable long lesson,@PathVariable String action,@RequestBody Action body,jakarta.servlet.http.HttpServletRequest request){var valid=sessions.validation(request.getSession(false),((vn.edu.multigame.auth.security.AuthPrincipal)caller.getPrincipal()).userId());return runtime.serial(caller,lesson,valid,()->service.act(caller,lesson,action,body.requestId(),body.revision(),body.pool(),body.k(),body.grades()));}
}
