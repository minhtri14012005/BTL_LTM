package vn.edu.multigame.academic.controller;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.academic.dto.ClassView;
import vn.edu.multigame.academic.service.AcademicService;

@RestController @Profile("mysql") @RequestMapping("/api/teaching/classes")
public class TeachingClassController {
    private final AcademicService service;
    public TeachingClassController(AcademicService service) {this.service=service;}
    @GetMapping public List<ClassView> classes(Authentication caller) {return service.assignedClasses(caller);}
    @GetMapping("/{id}") public ClassView course(Authentication caller,@PathVariable long id) {return service.assignedClass(caller,id);}
}
