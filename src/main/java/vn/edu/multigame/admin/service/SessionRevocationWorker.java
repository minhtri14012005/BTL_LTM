package vn.edu.multigame.admin.service;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
/** Durable session revocation survives a process failure after an account sanction commits. */
@Component @Profile("mysql")
public class SessionRevocationWorker {
 private final JdbcTemplate jdbc;private final AuthSessionRegistry sessions;private final AccountAccessPolicy accounts;
 public SessionRevocationWorker(JdbcTemplate jdbc,AuthSessionRegistry sessions,AccountAccessPolicy accounts){this.jdbc=jdbc;this.sessions=sessions;this.accounts=accounts;}
 @Scheduled(initialDelay=1000,fixedDelay=1000) public void reconcile(){
  for(var row:jdbc.queryForList("SELECT id,user_id,auth_cutoff_epoch FROM account_effect_intent WHERE state='PENDING' AND retry_after_ms<=? ORDER BY id LIMIT 64",System.currentTimeMillis())){
   long id=((Number)row.get("id")).longValue(),user=((Number)row.get("user_id")).longValue(),epoch=((Number)row.get("auth_cutoff_epoch")).longValue();
   try{
    var account=accounts.state(user);String reason=account.banned(System.currentTimeMillis())?account.code()+(account.untilMs()==null?"":":"+account.untilMs()):"SESSION_REVOKED";
    sessions.revokeThrough(user,epoch,reason);
    jdbc.update("UPDATE account_effect_intent SET state='DONE',completed_at_ms=? WHERE id=? AND state='PENDING'",System.currentTimeMillis(),id);
   }catch(RuntimeException failure){jdbc.update("UPDATE account_effect_intent SET attempts=LEAST(attempts+1,2147483647),retry_after_ms=? WHERE id=? AND state='PENDING'",System.currentTimeMillis()+2000,id);}
  }
 }
}
