package vn.edu.multigame.admin.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.admin.dto.OrphanCloseRequest;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.game.service.GameFailure;
import vn.edu.multigame.room.enums.RoomStatus;
import vn.edu.multigame.room.repository.RoomRepository;
import vn.edu.multigame.room.service.*;

@Service @Profile("mysql") @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
public class OrphanCloseTransactions {
    public record Receipt(long roomId,long revision,long effectiveAtMs,boolean replayed) {}
    public record Closed(Receipt receipt,vn.edu.multigame.room.dto.response.RoomResponse changed) {}
    private final RoomRepository rooms;
    private final RoomService views;
    private final AccountAccessPolicy accounts;
    private final AuthService users;
    private final JdbcTemplate jdbc;
    public OrphanCloseTransactions(RoomRepository rooms,RoomService views,AccountAccessPolicy accounts,AuthService users,JdbcTemplate jdbc){this.rooms=rooms;this.views=views;this.accounts=accounts;this.users=users;this.jdbc=jdbc;}
    public Closed close(Authentication auth,long id,OrphanCloseRequest input,String hash){
        long admin=users.requireAdmin(auth).getId();var room=rooms.findLockedById(id).orElseThrow(RoomFailure::missing);
        var prior=jdbc.query("select * from admin_orphan_close_action where admin_user_id=? and request_id=?",(r,n)->{
            if(r.getLong("room_id")!=id || !r.getString("payload_fingerprint").equals(hash))throw GameFailure.conflict("REQUEST_ID_REUSED");
            return new Receipt(id,r.getLong("room_revision"),r.getLong("effective_at_ms"),true);
        },admin,input.requestId());if(!prior.isEmpty())return new Closed(prior.getFirst(),null);
        var host=accounts.lock(room.getHostUserId());long now=System.currentTimeMillis();
        if(room.getStatus()!=RoomStatus.WAITING || jdbc.queryForObject("select count(*) from game_session where room_id=? and status='ACTIVE'",Long.class,id)>0)throw GameFailure.conflict("ORPHAN_WAITING_REQUIRED");
        if(!host.banned(now) && host.deletedAtMs()==null)throw GameFailure.conflict("HOST_STILL_AVAILABLE");
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void beforeCommit(boolean readOnly){if(host.deletedAtMs()==null && !host.banned(System.currentTimeMillis()))throw GameFailure.conflict("HOST_STILL_AVAILABLE");}
        });
        if(room.getRevision()!=input.revision())throw GameFailure.conflict("REVISION_CONFLICT");
        room.setStatus(RoomStatus.CLOSED);rooms.flush();
        // No session revocation or deletion of retained Game/Answer/History. Roster notification uses normal RoomChanged.
        jdbc.update("insert into admin_orphan_close_action(admin_user_id,request_id,payload_fingerprint,room_id,host_user_id,host_ban_version,reason,effective_at_ms,room_revision) values(?,?,?,?,?,?,?,?,?)",admin,input.requestId(),hash,id,room.getHostUserId(),host.version(),input.reason().strip(),now,room.getRevision());
        return new Closed(new Receipt(id,room.getRevision(),now,false),views.getRetainedForHost(room.getHostUserId(),id));
    }
}
