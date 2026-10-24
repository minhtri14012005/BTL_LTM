package vn.edu.multigame.admin.service;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.admin.dto.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.common.util.OperationFailure;

/** Locks the target account and records durable session revocation in the same transaction. */
@Service @Profile("mysql") @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
public class AdminSanctionTransactions {
    private final JdbcTemplate jdbc;
    private final AccountAccessPolicy access;
    private final AuthService users;
    public AdminSanctionTransactions(JdbcTemplate jdbc,AccountAccessPolicy access,AuthService users){
        this.jdbc=jdbc;this.access=access;this.users=users;
    }
    public SanctionResponse apply(Authentication auth,long user,String command,SanctionRequest input,String fingerprint){
        long admin=users.requireAdmin(auth).getId();
        AccountAccessPolicy.State before;
        try{before=access.lock(user);}catch(AuthFailure missing){throw new OperationFailure(HttpStatus.NOT_FOUND,"USER_NOT_FOUND");}
        var prior=receipt(admin,user,input.requestId(),fingerprint);if(prior!=null)return prior;
        if(admin==user || "ADMIN".equals(jdbc.queryForObject("select role from app_user where id=?",String.class,user)))throw OperationFailure.conflict("BAN_ADMIN_FORBIDDEN");
        if(before.deletedAtMs()!=null)throw OperationFailure.conflict("USER_UNAVAILABLE");
        if(before.version()!=input.expectedVersion())throw OperationFailure.conflict("STALE_VERSION");
        if(command.equals("TEMP")?(input.minutes()==null):(input.minutes()!=null))throw new AuthFailure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Thời lượng không hợp lệ.");
        long now=System.currentTimeMillis();boolean active=before.banned(now);
        if(command.equals("TEMP") && before.kind().equals("INDEFINITE"))throw OperationFailure.conflict("BAN_DOWNGRADE_FORBIDDEN");
        boolean changed=command.equals("TEMP") || command.equals("INDEFINITE") && !before.kind().equals("INDEFINITE") || command.equals("UNBAN") && active;
        if(changed && (before.version()==Long.MAX_VALUE || !command.equals("UNBAN") && before.epoch()==Long.MAX_VALUE))throw OperationFailure.conflict("SANCTION_VERSION_EXHAUSTED");
        String kind=changed?(command.equals("UNBAN")?"NONE":command):before.kind();
        Long until=changed?(command.equals("TEMP")?Math.addExact(now,Math.multiplyExact(input.minutes().longValue(),60_000L)):null):before.untilMs();
        long version=changed?Math.incrementExact(before.version()):before.version();
        boolean ban=changed && !command.equals("UNBAN");long epoch=ban?Math.incrementExact(before.epoch()):before.epoch();
        if(changed)jdbc.update("update app_user set ban_kind=?,banned_until_ms=?,ban_version=?,auth_epoch=? where id=?",kind,until,version,epoch,user);
        long action=insert("insert into admin_account_action(admin_user_id,target_user_id,request_id,payload_fingerprint,action,expected_version,duration_minutes,reason,result,before_kind,before_until_ms,after_kind,after_until_ms,committed_version,auth_cutoff_epoch,effective_at_ms) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                admin,user,input.requestId(),fingerprint,command,input.expectedVersion(),input.minutes(),input.reason().strip(),changed?"APPLIED":"NO_CHANGE",before.kind(),before.untilMs(),kind,until,version,epoch,now);
        if(ban){
            access.fenceForCommit(user,epoch);
            intent(action,user,epoch);

        }
        return receipt(admin,user,input.requestId(),fingerprint,false);
    }
    @Transactional(readOnly=true) public SanctionResponse receipt(long admin,long user,String request,String fingerprint){return receipt(admin,user,request,fingerprint,true);}
    private SanctionResponse receipt(long admin,long user,String request,String fingerprint,boolean replayed){
        var rows=jdbc.query("select * from admin_account_action where admin_user_id=? and request_id=?",(r,n)->{
            if(r.getLong("target_user_id")!=user || !r.getString("payload_fingerprint").equals(fingerprint))throw OperationFailure.conflict("REQUEST_ID_REUSED");
            long action=r.getLong("id");return new SanctionResponse(request,action,user,r.getString("action"),r.getString("result"),r.getString("after_kind"),r.getObject("after_until_ms",Long.class),r.getLong("committed_version"),r.getLong("effective_at_ms"),replayed,access.state(user),System.currentTimeMillis(),jdbc.queryForObject("select count(*) from account_effect_intent where action_id=? and state='PENDING'",Integer.class,action));
        },admin,request);return rows.isEmpty()?null:rows.getFirst();
    }
    private void intent(long action,long user,long cutoff){
        jdbc.update("insert into account_effect_intent(action_id,user_id,auth_cutoff_epoch) values(?,?,?)",action,user,cutoff);
    }
    private long insert(String sql,Object... values){var keys=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p;},keys);return keys.getKey().longValue();}
}
