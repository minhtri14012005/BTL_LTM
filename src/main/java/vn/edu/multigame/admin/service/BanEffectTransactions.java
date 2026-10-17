package vn.edu.multigame.admin.service;

import jakarta.persistence.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.admin.repository.BanEffectRepository;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
import vn.edu.multigame.game.repository.GameSessionRepository;
import vn.edu.multigame.game.enums.GameStatus;
import vn.edu.multigame.game.service.*;
import vn.edu.multigame.room.repository.*;
import vn.edu.multigame.room.service.RoomService;
import vn.edu.multigame.room.dto.response.RoomResponse;

/** Deferred effects always use captured IDs. No account-state recheck can erase a ban's old targets. */
@Service @Profile("mysql") @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
public class BanEffectTransactions {
    public record Applied(CommittedGame game,RoomResponse room) {}
    private final BanEffectRepository intents;
    private final RoomRepository rooms;
    private final RoomMemberRepository members;
    private final GameSessionRepository games;
    private final AccountAccessPolicy accounts;
    private final GameProjection projection;
    private final RoomService views;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @PersistenceContext EntityManager entities;
    public BanEffectTransactions(BanEffectRepository intents,RoomRepository rooms,RoomMemberRepository members,GameSessionRepository games,AccountAccessPolicy accounts,GameProjection projection,RoomService views,org.springframework.jdbc.core.JdbcTemplate jdbc){
        this.intents=intents;this.rooms=rooms;this.members=members;this.games=games;this.accounts=accounts;this.projection=projection;this.views=views;this.jdbc=jdbc;
    }
    public Applied game(long id){
        var target=intents.get(id);var room=rooms.findLockedById(target.room()).orElse(null);
        var game=games.findById(target.game()).orElse(null);
        if(room==null || game==null)return missing(target);
        entities.refresh(game,LockModeType.PESSIMISTIC_WRITE);
        accounts.lock(target.userId());if(intents.done(id))return new Applied(null,null);
        jdbc.update("delete from user_active_game where user_id=? and game_session_id=?",target.userId(),target.game());
        int detached=detach(target);
        if(detached>0){bumpRoom(room);}
        if(game.getStatus()==GameStatus.ACTIVE){
            if(games.touchRevision(game.getId(),game.getRevision())!=1)throw GameFailure.conflict("REVISION_CONFLICT");entities.refresh(game);
        }
        intents.complete(id);
        return new Applied(projection.copy(game),detached>0?views.getRetainedForHost(room.getHostUserId(),room.getId()):null);
    }
    public Applied membership(long id){
        var target=intents.get(id);var room=rooms.findLockedById(target.room()).orElse(null);
        if(room==null)return missing(target);
        accounts.lock(target.userId());if(intents.done(id))return new Applied(null,null);
        int detached=detach(target);if(detached>0)bumpRoom(room);intents.complete(id);
        return new Applied(null,detached>0?views.getRetainedForHost(room.getHostUserId(),room.getId()):null);
    }
    private int detach(BanEffectRepository.Intent t){return t.member()==null?0:members.detachGeneration(t.member(),t.room(),t.userId(),t.generation(),Math.max(t.effectiveAt(),System.currentTimeMillis()));}
    private Applied missing(BanEffectRepository.Intent target){
        accounts.lock(target.userId());if(!intents.done(target.id())){
            if(target.game()!=null)jdbc.update("delete from user_active_game where user_id=? and game_session_id=?",target.userId(),target.game());
            intents.complete(target.id());
        }return new Applied(null,null);
    }
    private void bumpRoom(vn.edu.multigame.room.entity.Room room){
        if(room.getRevision()==Long.MAX_VALUE || jdbc.update("update room set revision=revision+1 where id=? and revision=?",room.getId(),room.getRevision())!=1)throw GameFailure.conflict("REVISION_CONFLICT");entities.refresh(room);
    }
    public void complete(long id){if(!intents.done(id))intents.complete(id);}
}
