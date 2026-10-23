package vn.edu.multigame.lesson.service;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.lesson.repository.*;
import vn.edu.multigame.auth.service.AuthService;
@Service @Profile("mysql") @Transactional(readOnly=true)
public class AssignmentHistoryService {
 private final AssignmentStore assignments;private final LessonService lessons;private final AuthService auth;
 public AssignmentHistoryService(AssignmentStore assignments,LessonService lessons,AuthService auth){this.assignments=assignments;this.lessons=lessons;this.auth=auth;}
 public List<AssignmentStore.Submission> history(Authentication caller,long lesson,int position,Long student,long after,int limit){
  var user=auth.requireActiveUser(caller);boolean teacher=user.getRole()==vn.edu.multigame.user.enums.UserRole.GIANG_VIEN;
  var view=lessons.get(caller,lesson,teacher);var game=view.games().stream().filter(g->g.position()==position && g.mode()==vn.edu.multigame.lesson.domain.LessonMode.ASSIGNMENT).findFirst().orElseThrow(()->LessonFailure.conflict("ASSIGNMENT_NOT_FOUND"));
  if(after<0 || limit<1 || limit>100 || teacher && (student==null || student<1))throw new LessonFailure(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_REQUEST");
  return assignments.history(game.id(),teacher?student:user.getId(),after,limit);
 }
}
