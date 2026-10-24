package vn.edu.multigame.achievements.service;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.achievements.repository.LearningProfileRepository;
import vn.edu.multigame.achievements.dto.LearningProfile;
@Service @Profile("mysql") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class LearningProfileService {
 private final LearningProfileRepository profiles;private final AuthService auth;
 public LearningProfileService(LearningProfileRepository profiles,AuthService auth){this.profiles=profiles;this.auth=auth;}
 private long target(Authentication caller,Long requested){var user=auth.requireActiveUser(caller);long id=requested==null?user.getId():requested;if(id!=user.getId())auth.requireAdmin(caller);return id;}
 public LearningProfile profile(Authentication caller,Long requested){long id=target(caller,requested);var user=profiles.identity(id).orElseThrow(()->new vn.edu.multigame.common.util.OperationFailure(org.springframework.http.HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND"));return new LearningProfile(id,user.name(),user.code(),user.status(),profiles.stats(id),profiles.awards(id,null,null));}
 public LearningProfile.History history(Authentication caller,Long requested,Long subject,Long year,Long course,int page,int size){long id=target(caller,requested);if(page<0 || page>1000000 || size<1 || size>50)throw new vn.edu.multigame.common.util.OperationFailure(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_PAGE");if(subject!=null && subject<1 || year!=null && year<1 || course!=null && course<1)throw new vn.edu.multigame.common.util.OperationFailure(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_SCOPE");return profiles.history(id,subject,year,course,page,size);}
 public java.util.List<LearningProfile.Award> awards(Authentication caller,Long requested,Long subject,Long year){return profiles.awards(target(caller,requested),subject,year);}
}
