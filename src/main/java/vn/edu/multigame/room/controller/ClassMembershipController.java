package vn.edu.multigame.room.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.room.dto.request.ClassMembershipRequests.*;
import vn.edu.multigame.room.dto.response.*;
import vn.edu.multigame.room.service.ClassMembershipService;

@RestController @Profile("mysql")
public class ClassMembershipController {
    private final ClassMembershipService service;
    public ClassMembershipController(ClassMembershipService service) {this.service=service;}
    @GetMapping("/api/student/classes") public List<ClassMembershipView> mine(Authentication caller) {return service.mine(caller);}
    @PostMapping("/api/student/classes/join") public ClassMembershipView join(Authentication caller,@Valid @RequestBody Join input) {return service.join(caller,input);}
    @GetMapping({"/api/student/classes/{classId}/room","/api/teaching/classes/{classId}/room","/api/admin/academic/classes/{classId}/room"})
    public ClassRoomView room(Authentication caller,@PathVariable long classId) {return service.room(caller,classId);}
    @GetMapping("/api/teaching/classes/{classId}/members")
    public List<ClassMembershipView> roster(Authentication caller,@PathVariable long classId) {return service.roster(caller,classId);}
    @PostMapping("/api/teaching/classes/{classId}/members/{student}/approve")
    public ClassMembershipView approve(Authentication caller,@PathVariable long classId,@PathVariable long student,@Valid @RequestBody Decision input) {return service.decide(caller,classId,student,"APPROVE",input);}
    @PostMapping("/api/teaching/classes/{classId}/members/{student}/reject")
    public ClassMembershipView reject(Authentication caller,@PathVariable long classId,@PathVariable long student,@Valid @RequestBody Decision input) {return service.decide(caller,classId,student,"REJECT",input);}
    @PostMapping("/api/teaching/classes/{classId}/members/{student}/remove")
    public ClassMembershipView remove(Authentication caller,@PathVariable long classId,@PathVariable long student,@Valid @RequestBody Decision input) {return service.decide(caller,classId,student,"REMOVE",input);}
    @PostMapping("/api/teaching/classes/{classId}/members/{student}/restore")
    public ClassMembershipView restore(Authentication caller,@PathVariable long classId,@PathVariable long student,@Valid @RequestBody Decision input){return service.decide(caller,classId,student,"RESTORE",input);}
    @PostMapping("/api/teaching/classes/{classId}/members/approve-all")
    public ClassMembershipService.BulkResult approveAll(Authentication caller,@PathVariable long classId,@Valid @RequestBody Batch input){return service.approveAll(caller,classId,input.requestId());}
}
