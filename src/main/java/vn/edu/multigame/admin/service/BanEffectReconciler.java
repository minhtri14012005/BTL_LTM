package vn.edu.multigame.admin.service;

import java.util.Set;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.multigame.admin.repository.BanEffectRepository;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
import vn.edu.multigame.game.service.GameStartupCleanup;
import vn.edu.multigame.realtime.session.*;
import vn.edu.multigame.room.service.RoomChanged;

/** Bounded asynchronous reconciliation; restart discovers durable pending IDs, never current targets. */
@Service @Profile("mysql")
public class BanEffectReconciler {
    private final BanEffectRepository intents;
    private final BanEffectTransactions effects;
    private final GameRuntime games;
    private final RoomBoundary rooms;
    private final AuthSessionRegistry sessions;
    private final GameStartupCleanup startup;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<SanctionReservationPort> reservations;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.auth.service.AccountAccessPolicy accounts;
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    private final Semaphore capacity=new Semaphore(16);
    private final ExecutorService workers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ban-effect-",0).factory());
    public BanEffectReconciler(BanEffectRepository intents,BanEffectTransactions effects,GameRuntime games,RoomBoundary rooms,AuthSessionRegistry sessions,GameStartupCleanup startup,ApplicationEventPublisher events,ObjectProvider<SanctionReservationPort> reservations,org.springframework.jdbc.core.JdbcTemplate jdbc){
        this.intents=intents;this.effects=effects;this.games=games;this.rooms=rooms;this.sessions=sessions;this.startup=startup;this.events=events;this.reservations=reservations;this.jdbc=jdbc;
    }
    @Scheduled(initialDelay=1000,fixedDelay=1000) public void reconcile(){
        if(!startup.ready())return;
        for(long id:intents.pending()){
            if(!capacity.tryAcquire())return;
            if(!running.add(id)){capacity.release();continue;}
            try{workers.execute(()->{try{dispatch(id);}catch(Exception failure){try{intents.retry(id);}catch(RuntimeException unavailable){/* Durable PENDING remains. */}}
                finally{running.remove(id);capacity.release();}});}
            catch(RejectedExecutionException closing){running.remove(id);capacity.release();}
        }
    }
    public void dispatch(long id)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Release User transaction before reconciliation");
        var target=intents.get(id);if(target.state().equals("DONE"))return;
        switch(target.type()){
            case "SESSIONS" -> {var account=accounts.state(target.userId());String reason=account.banned(System.currentTimeMillis())?account.code()+(account.untilMs()==null?"":":"+account.untilMs()):"SESSION_REVOKED";sessions.revokeThrough(target.userId(),target.cutoff(),reason);effects.complete(id);}
            case "GAME" -> {
                boolean active=jdbc.queryForObject("select count(*) from game_session where id=? and status='ACTIVE'",Long.class,target.game())>0;
                if(active && games.installed(target.game()))games.administrative(target.game(),()->{
                    var applied=effects.game(id);return new GameRuntime.AdministrativeResult<>(true,applied.game(),()->publish(applied));
                }).get(8,TimeUnit.SECONDS);
                else {
                    // Startup keeps SERVER_INTERRUPTED. Never mutate an uninstalled ACTIVE actor out of queue.
                    if(active)throw new IllegalStateException("Active Game runtime pending");
                    rooms.read(RoomBoundary.Scope.room(target.room()),()->{var applied=effects.game(id);publish(applied);return true;});
                }
            }
            case "MEMBERSHIP" -> rooms.read(RoomBoundary.Scope.room(target.room()),()->{var applied=effects.membership(id);publish(applied);return true;});
            case "RESERVATION" -> {
                var port=reservations.getIfAvailable();if(port==null)throw new IllegalStateException("Reservation release requires T15 adapter");
                port.release(target.reservation(),target.reservationVersion(),target.actionId());effects.complete(id);
            }
            default -> throw new IllegalStateException("Unknown durable effect");
        }
    }
    private void publish(BanEffectTransactions.Applied applied){if(applied.room()!=null)events.publishEvent(new RoomChanged(applied.room()));}
    @PreDestroy public void close(){workers.shutdownNow();}
}
