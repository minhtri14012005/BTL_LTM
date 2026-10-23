package vn.edu.multigame.academic.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.academic.dto.*;
import vn.edu.multigame.academic.service.AcademicService;

@RestController @Profile("mysql") @RequestMapping("/api/admin/academic")
public class AcademicAdminController {
    private final AcademicService service;
    public AcademicAdminController(AcademicService service) {this.service=service;}
    @GetMapping("/years") public List<AcademicViews.Year> years(Authentication caller) {return service.years(caller);}
    @GetMapping("/semesters") public List<AcademicViews.Semester> semesters(Authentication caller) {return service.semesters(caller);}
    @GetMapping("/subjects") public List<AcademicViews.Subject> subjects(Authentication caller) {return service.subjects(caller);}
    @GetMapping("/offerings") public List<AcademicViews.Offering> offerings(Authentication caller) {return service.offerings(caller);}
    @GetMapping("/classes") public List<ClassView> classes(Authentication caller) {return service.classes(caller);}
    @GetMapping("/classes/{id}") public ClassView course(Authentication caller,@PathVariable long id) {return service.classDetail(caller,id);}
    @PostMapping("/years") @ResponseStatus(HttpStatus.CREATED)
    public AcademicViews.Year year(Authentication caller,@Valid @RequestBody AcademicRequests.Year input) {return service.createYear(caller,input);}
    @PostMapping("/semesters") @ResponseStatus(HttpStatus.CREATED)
    public AcademicViews.Semester semester(Authentication caller,@Valid @RequestBody AcademicRequests.Semester input) {return service.createSemester(caller,input);}
    @PostMapping("/subjects") @ResponseStatus(HttpStatus.CREATED)
    public AcademicViews.Subject subject(Authentication caller,@Valid @RequestBody AcademicRequests.Subject input) {return service.createSubject(caller,input);}
    @PostMapping("/offerings") @ResponseStatus(HttpStatus.CREATED)
    public AcademicViews.Offering offering(Authentication caller,@Valid @RequestBody AcademicRequests.Offering input) {return service.createOffering(caller,input);}
    @PostMapping("/classes") @ResponseStatus(HttpStatus.CREATED)
    public ClassView course(Authentication caller,@Valid @RequestBody AcademicRequests.CourseClass input) {return service.createClass(caller,input);}
    @PutMapping("/classes/{id}/teachers/{teacherId}")
    public ClassView assign(Authentication caller,@PathVariable long id,@PathVariable long teacherId) {return service.assign(caller,id,teacherId);}
    @PutMapping("/years/{id}") public AcademicViews.Year updateYear(Authentication caller,@PathVariable long id,@Valid @RequestBody AcademicRequests.Year input){return service.updateYear(caller,id,input);}
    @PutMapping("/semesters/{id}") public AcademicViews.Semester updateSemester(Authentication caller,@PathVariable long id,@Valid @RequestBody AcademicRequests.Semester input){return service.updateSemester(caller,id,input);}
    @PutMapping("/subjects/{id}") public AcademicViews.Subject updateSubject(Authentication caller,@PathVariable long id,@Valid @RequestBody AcademicRequests.Subject input){return service.updateSubject(caller,id,input);}
    @DeleteMapping("/years/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteYear(Authentication caller,@PathVariable long id){service.deleteYear(caller,id);}
    @DeleteMapping("/semesters/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSemester(Authentication caller,@PathVariable long id){service.deleteSemester(caller,id);}
    @DeleteMapping("/subjects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSubject(Authentication caller,@PathVariable long id){service.deleteSubject(caller,id);}
}
