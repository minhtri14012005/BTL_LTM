package vn.edu.multigame.achievements.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import vn.edu.multigame.achievements.service.LearningProfileService;
import vn.edu.multigame.achievements.dto.LearningProfile;
@RestController @Profile("mysql")
public class LearningProfileController {
 private final LearningProfileService profiles;
 public LearningProfileController(LearningProfileService profiles){this.profiles=profiles;}
 @GetMapping("/api/learning/profile") public LearningProfile profile(Authentication caller,@RequestParam(required=false) Long userId){return profiles.profile(caller,userId);}
 @GetMapping("/api/learning/history") public LearningProfile.History history(Authentication caller,@RequestParam(required=false) Long userId,@RequestParam(required=false) Long subjectId,@RequestParam(required=false) Long academicYearId,@RequestParam(required=false) Long classId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return profiles.history(caller,userId,subjectId,academicYearId,classId,page,size);}
 @GetMapping("/api/learning/achievements") public java.util.List<LearningProfile.Award> awards(Authentication caller,@RequestParam(required=false) Long userId,@RequestParam(required=false) Long subjectId,@RequestParam(required=false) Long academicYearId){return profiles.awards(caller,userId,subjectId,academicYearId);}
}
