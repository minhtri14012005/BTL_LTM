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
    @PutMapping("/{id}") public UserResponse edit(Authentication caller,@PathVariable long id,@Valid @RequestBody vn.edu.multigame.admin.dto.TeacherChanges.Edit input){return service.edit(caller,id,input);}
    @PostMapping("/{id}/password") public void password(Authentication caller,@PathVariable long id,@Valid @RequestBody vn.edu.multigame.admin.dto.TeacherChanges.Password input){service.password(caller,id,input);}
    @PostMapping("/{id}/delete") public void delete(Authentication caller,@PathVariable long id,@Valid @RequestBody vn.edu.multigame.admin.dto.TeacherChanges.Delete input){service.delete(caller,id,input);}
}
