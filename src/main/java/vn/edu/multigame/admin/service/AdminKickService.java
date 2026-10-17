package vn.edu.multigame.admin.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.edu.multigame.admin.dto.*;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.game.enums.GameStatus;
import vn.edu.multigame.game.repository.GameSessionRepository;
import vn.edu.multigame.game.service.GameFailure;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;
import vn.edu.multigame.realtime.session.GameRuntime;

@Service @Profile("mysql")
public class AdminKickService {
    private final AdminReadService access;
    private final AuthService users;
    private final AdminKickTransactions writes;
    private final GameRuntime runtime;
    private final GameSessionRepository games;
    private final AuthenticatedSocketRegistry sockets;
    private final CommandFingerprint fingerprints;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final vn.edu.multigame.room.service.RoomService rooms;
    public AdminKickService(AdminReadService access,AuthService users,AdminKickTransactions writes,GameRuntime runtime,
            GameSessionRepository games,AuthenticatedSocketRegistry sockets,CommandFingerprint fingerprints,org.springframework.context.ApplicationEventPublisher events,vn.edu.multigame.room.service.RoomService rooms){
        this.access=access;this.users=users;this.writes=writes;this.runtime=runtime;this.games=games;this.sockets=sockets;this.fingerprints=fingerprints;
        this.events=events;this.rooms=rooms;
    }
    public CompletableFuture<KickResponse> kick(Authentication auth,String session,long game,KickRequest input){
        access.requireAccess(auth,session);long admin=users.requireAdmin(auth).getId();
        String fingerprint=fingerprints.of("KICK_GAME","GAME",game,null,Map.of("userId",input.userId(),"reason",input.reason().strip()));
        var prior=writes.receipt(admin,game,input,fingerprint);if(prior!=null)return CompletableFuture.completedFuture(prior);
        if(games.findById(game).orElseThrow(()->new GameFailure(org.springframework.http.HttpStatus.NOT_FOUND,"GAME_NOT_FOUND")).getStatus()!=GameStatus.ACTIVE)throw GameFailure.conflict("GAME_NOT_ACTIVE");
        return runtime.administrative(game,()->{
            access.requireAccess(auth,session);
            var committed=writes.apply(auth,game,input,fingerprint);
            return new GameRuntime.AdministrativeResult<>(committed.response(),committed.game(),()->{
                if(!committed.changed())return;
                sockets.gameRestricted(game,committed.roomId(),input.userId());
                long host=committed.game().publicView().members().stream().filter(m->m.role()==vn.edu.multigame.game.enums.MemberRole.HOST).findFirst().orElseThrow().userId();
                events.publishEvent(new vn.edu.multigame.room.service.RoomChanged(rooms.getRetainedForHost(host,committed.roomId())));
            });
        });
    }
}
