package vn.edu.multigame.lesson.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.lesson.service.LessonService;

@RestController @Profile("mysql")
public class LessonController {
    private final LessonService service;
    private final vn.edu.multigame.realtime.session.LessonRuntime runtime;
    private final vn.edu.multigame.auth.security.AuthSessionRegistry sessions;
    public LessonController(LessonService service,vn.edu.multigame.realtime.session.LessonRuntime runtime,vn.edu.multigame.auth.security.AuthSessionRegistry sessions) {this.service=service;this.runtime=runtime;this.sessions=sessions;}
    private Runnable session(Authentication caller,jakarta.servlet.http.HttpServletRequest request) {
        return sessions.validation(request.getSession(false),((vn.edu.multigame.auth.security.AuthPrincipal)caller.getPrincipal()).userId());
    }
    @GetMapping("/api/teaching/classes/{classId}/lessons")
    public List<LessonView> teacherList(Authentication caller,@PathVariable long classId) {return service.list(caller,classId,true);}
    @GetMapping("/api/student/classes/{classId}/lessons")
    public List<LessonView> studentList(Authentication caller,@PathVariable long classId) {return service.list(caller,classId,false);}
    @GetMapping("/api/teaching/classes/{classId}/lesson-progress")
    public LessonView.Progress progress(Authentication caller,@PathVariable long classId) {return service.progress(caller,classId);}
    @PostMapping("/api/teaching/classes/{classId}/lessons") @ResponseStatus(HttpStatus.CREATED)
    public LessonView create(Authentication caller,@PathVariable long classId,@Valid @RequestBody LessonRequests.Plan input) {return service.create(caller,classId,input);}
    @GetMapping("/api/teaching/lessons/{id}")
    public LessonView teacherGet(Authentication caller,@PathVariable long id) {return service.get(caller,id,true);}
    @GetMapping("/api/student/lessons/{id}")
    public LessonView studentGet(Authentication caller,@PathVariable long id) {return service.get(caller,id,false);}
    @PutMapping("/api/teaching/lessons/{id}")
    public LessonView edit(Authentication caller,@PathVariable long id,@Valid @RequestBody LessonRequests.Edit input) {return service.edit(caller,id,input);}
    @PutMapping("/api/teaching/lessons/{id}/visibility")
    public LessonView visible(Authentication caller,@PathVariable long id,@Valid @RequestBody LessonRequests.Visibility input) {return service.visible(caller,id,input);}
    @PostMapping("/api/teaching/lessons/{id}/start")
    public LessonView start(Authentication caller,@PathVariable long id,@Valid @RequestBody LessonRequests.Transition input,jakarta.servlet.http.HttpServletRequest request) {return runtime.serial(caller,id,session(caller,request),()->service.start(caller,id,input.revision()));}
    @PostMapping("/api/teaching/lessons/{id}/games/{position}/start")
    public LessonView startGame(Authentication caller,@PathVariable long id,@PathVariable int position,@Valid @RequestBody LessonRequests.Transition input,jakarta.servlet.http.HttpServletRequest request) {return runtime.serial(caller,id,session(caller,request),()->service.startGame(caller,id,position,input.revision()));}
    @PostMapping("/api/student/lessons/{id}/attend")
    public LessonView attend(Authentication caller,@PathVariable long id,jakarta.servlet.http.HttpServletRequest request) {return runtime.serial(caller,id,session(caller,request),()->service.attend(caller,id,null));}
    @PostMapping("/api/student/lessons/{id}/games/{position}/participate")
    public LessonView participate(Authentication caller,@PathVariable long id,@PathVariable int position,jakarta.servlet.http.HttpServletRequest request) {return runtime.serial(caller,id,session(caller,request),()->service.attend(caller,id,position));}
}
