package vn.edu.multigame.admin.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.admin.dto.TeacherRequest;
import vn.edu.multigame.admin.service.TeacherAccountService;
import vn.edu.multigame.auth.dto.response.UserResponse;

@RestController @Profile("mysql") @RequestMapping("/api/admin/teachers")
public class TeacherAccountController {
    private final TeacherAccountService service;
    public TeacherAccountController(TeacherAccountService service) { this.service=service; }
    @PostMapping
    ResponseEntity<UserResponse> create(Authentication caller, @Valid @RequestBody TeacherRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(caller,input));
    }
}
