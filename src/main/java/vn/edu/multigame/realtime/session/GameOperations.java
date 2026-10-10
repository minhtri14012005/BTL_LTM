package vn.edu.multigame.realtime.session;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.StartGameResponse;
import vn.edu.multigame.game.service.GameTransactions;
@Component @Profile("mysql")
public class GameOperations {
    private final RoomOperations rooms;
    private final AuthService users;
    private final GameTransactions transactions;
    private final GameRuntime runtime;
    private final vn.edu.multigame.realtime.timer.ServerClock clock;
    public GameOperations(RoomOperations rooms,AuthService users,GameTransactions transactions,GameRuntime runtime,vn.edu.multigame.realtime.timer.ServerClock clock) {
        this.rooms=rooms; this.users=users; this.transactions=transactions; this.runtime=runtime;
        this.clock=clock;
    }
    public StartGameResponse start(Authentication auth,long roomId,StartGameRequest request,Runnable validateConnection) {
        long uid=users.requireActiveUser(auth).getId();
        var held=new AtomicReference<GameRuntime.Reservation>(); var committed=new AtomicReference<GameTransactions.Started>();
        var receipt=rooms.execute(auth,roomId,request.requestId(),"START_GAME",Map.of("revision",request.revision(),"questionCount",request.questionCount()),null,
                validateConnection,() -> {
                    var reservation=runtime.reserve(); held.set(reservation);
                    try { var started=transactions.start(uid,roomId,request.revision(),request.questionCount(),clock.sample().epochMs()); committed.set(started); return started.room(); }
                    catch(RuntimeException failure) { reservation.close(); throw failure; }
                },(saved,replay) -> {
                    // A retained internal v2 receipt must not bypass the public runtime gate.
                    if(replay && saved.snapshot().configVersion()==2 && !runtime.installed(saved.gameSessionId()) && transactions.read(saved.gameSessionId(),uid).publicView().status()!=vn.edu.multigame.game.enums.GameStatus.FINISHED) throw vn.edu.multigame.game.service.GameFailure.conflict("RULES_NOT_IMPLEMENTED");
                    if(!replay) {
                        try { runtime.install(committed.get().game(),held.get()); }
                        catch(RuntimeException failure) {
                            runtime.failedInstallation(committed.get().game()); held.get().close();
                            throw vn.edu.multigame.game.service.GameFailure.unavailable();
                        }
                    }
                });
        return new StartGameResponse(receipt.gameSessionId(),receipt.snapshot(),receipt.serverTimeMs());
    }
}
