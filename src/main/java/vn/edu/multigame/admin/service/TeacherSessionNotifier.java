package vn.edu.multigame.admin.service;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
@Component @Profile("mysql")
public class TeacherSessionNotifier {
 private final AuthSessionRegistry sessions;
 public TeacherSessionNotifier(AuthSessionRegistry sessions){this.sessions=sessions;}
 @TransactionalEventListener public void changed(TeacherSessionChanged event){sessions.revokeThrough(event.userId(),event.epoch(),"SESSION_REVOKED");}
}
