package vn.edu.multigame.admin.service;

import java.util.*;
import jakarta.persistence.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.admin.dto.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.repository.*;
import vn.edu.multigame.game.service.*;
import vn.edu.multigame.room.repository.*;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.user.repository.UserRepository;
import vn.edu.multigame.user.enums.UserRole;

/** Called only through GameRuntime's authoritative queue. All durable effects share one TX. */
@Service @Profile("mysql")
@Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
public class AdminKickTransactions {
    public record Committed(KickResponse response,CommittedGame game,long roomId,boolean changed) {}
    private final JdbcTemplate jdbc;
    private final GameSessionRepository games;
    private final GameMemberRepository members;
    private final RoomRepository rooms;
    private final RoomMemberRepository memberships;
    private final UserRepository accounts;
    private final AuthService users;
    private final GameProjection projection;
    private final GameAccessPolicy access;
    @PersistenceContext EntityManager entities;
    public AdminKickTransactions(JdbcTemplate jdbc,GameSessionRepository games,GameMemberRepository members,RoomRepository rooms,
            RoomMemberRepository memberships,UserRepository accounts,AuthService users,GameProjection projection,GameAccessPolicy access){
        this.jdbc=jdbc;this.games=games;this.members=members;this.rooms=rooms;this.memberships=memberships;this.accounts=accounts;this.users=users;this.projection=projection;this.access=access;
    }
    @Transactional(readOnly=true) public KickResponse receipt(long admin,long game,KickRequest input,String fingerprint){
        var found=jdbc.query("select * from admin_kick_action where admin_user_id=? and request_id=?",(r,n)->{
            if(r.getLong("game_session_id")!=game || !r.getString("payload_fingerprint").equals(fingerprint))throw GameFailure.conflict("REQUEST_ID_REUSED");
            return new KickResponse(input.requestId(),r.getLong("id"),game,r.getLong("target_user_id"),r.getString("result"),r.getLong("effective_at_ms"),r.getLong("game_revision"),r.getBoolean("occupancy_released"),r.getBoolean("membership_detached"),true);
        },admin,input.requestId());return found.isEmpty()?null:found.getFirst();
    }
    public Committed apply(Authentication auth,long gameId,KickRequest input,String fingerprint){
        var game=games.findById(gameId).orElseThrow(()->new GameFailure(org.springframework.http.HttpStatus.NOT_FOUND,"GAME_NOT_FOUND"));
        var room=rooms.findLockedById(game.getRoomId()).orElseThrow();
        entities.refresh(game,LockModeType.PESSIMISTIC_WRITE);
        long adminId=users.requireAdmin(auth).getId();
        var locked=new HashMap<Long,vn.edu.multigame.user.entity.UserAccount>();
        java.util.stream.LongStream.of(adminId,input.userId()).distinct().sorted().forEach(id->locked.put(id,accounts.findLockedById(id).orElseThrow(()->id==adminId?AuthService.unauthorized():GameFailure.conflict("KICK_PLAYER_REQUIRED"))));
        entities.refresh(locked.get(adminId));
        if(locked.get(adminId).getRole()!=UserRole.ADMIN || locked.get(adminId).getDeletedAtMs()!=null)throw new AuthFailure(org.springframework.http.HttpStatus.FORBIDDEN,"FORBIDDEN","Cần quyền ADMIN.");
        var prior=receipt(adminId,gameId,input,fingerprint);if(prior!=null)return new Committed(prior,null,room.getId(),false);
        if(game.getStatus()!=GameStatus.ACTIVE)throw GameFailure.conflict("GAME_NOT_ACTIVE");
        var member=members.findByGameSessionIdAndUserId(gameId,input.userId()).orElseThrow(()->GameFailure.conflict("KICK_PLAYER_REQUIRED"));
        if(member.getRole()==MemberRole.HOST || room.getHostUserId()==input.userId())throw GameFailure.conflict("KICK_HOST_FORBIDDEN");
        if(locked.get(input.userId()).getRole()==UserRole.ADMIN || adminId==input.userId())throw GameFailure.conflict("KICK_ADMIN_FORBIDDEN");
        if(member.getParticipation()!=Participation.PLAYER)throw GameFailure.conflict("KICK_PLAYER_REQUIRED");
        var membership=memberships.findByRoomIdAndUserId(room.getId(),input.userId()).orElseThrow();
        boolean already=access.restricted(gameId,input.userId());long now=Math.max(System.currentTimeMillis(),game.getStartedAtMs());
        int released=0,detached=0;
        if(!already){
            access.fenceForCommit(gameId,input.userId());
            released=jdbc.update("delete from user_active_game where user_id=? and game_session_id=?",input.userId(),gameId);
            detached=memberships.detachGeneration(membership.getId(),room.getId(),input.userId(),member.getMembershipGeneration(),now);
            if(detached>0){
                if(room.getRevision()==Long.MAX_VALUE || jdbc.update("update room set revision=revision+1 where id=? and revision=?",room.getId(),room.getRevision())!=1)throw GameFailure.conflict("REVISION_CONFLICT");
                entities.refresh(room);
            }
            if(games.touchRevision(gameId,game.getRevision())!=1)throw GameFailure.conflict("REVISION_CONFLICT");
            entities.refresh(game);
        }
        long revision=game.getRevision();String status=already?"ALREADY_APPLIED":"KICK_APPLIED";int releaseCount=released,detachCount=detached;
        var keys=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement("insert into admin_kick_action(admin_user_id,request_id,payload_fingerprint,game_session_id,target_user_id,room_id,membership_row_id,membership_generation,reason,result,effective_at_ms,game_revision,occupancy_released,membership_detached) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            Object[] values={adminId,input.requestId(),fingerprint,gameId,input.userId(),room.getId(),membership.getId(),member.getMembershipGeneration(),input.reason().strip(),status,now,revision,releaseCount>0,detachCount>0};for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p;},keys);
        long action=keys.getKey().longValue();
        if(!already)jdbc.update("insert into game_participation_restriction(game_session_id,user_id,action_id,kind,effective_at_ms) values(?,?,?,'KICKED_BY_ADMIN',?)",gameId,input.userId(),action,now);
        rooms.flush();
        return new Committed(new KickResponse(input.requestId(),action,gameId,input.userId(),status,now,revision,released>0,detached>0,false),already?null:projection.copy(game),room.getId(),!already);
    }
}
