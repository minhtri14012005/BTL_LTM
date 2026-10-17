package vn.edu.multigame.achievements.service;

import java.util.Set;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.multigame.achievements.repository.AwardRepository;
import vn.edu.multigame.game.service.GameStartupCleanup;
import vn.edu.multigame.realtime.session.RoomBoundary;

/** Durable discovery, independent of GAME_END delivery or a retained terminal Game actor. */
@Service @Profile("mysql")
public class AwardReconciler {
    private final AwardRepository awards;private final AwardTransactions writes;private final RoomBoundary rooms;private final JdbcTemplate jdbc;private final GameStartupCleanup startup;
    private final Set<Long> running=ConcurrentHashMap.newKeySet();private final Semaphore slots=new Semaphore(4);
    private final ExecutorService workers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("award-",0).factory());
    public AwardReconciler(AwardRepository awards,AwardTransactions writes,RoomBoundary rooms,JdbcTemplate jdbc,GameStartupCleanup startup){this.awards=awards;this.writes=writes;this.rooms=rooms;this.jdbc=jdbc;this.startup=startup;}
    @Scheduled(initialDelayString="${quiz.awards.initial-delay-ms:1000}",fixedDelay=1000) public void reconcile(){
        if(!startup.ready())return;
        for(long id:awards.pending()){
            if(!slots.tryAcquire())return;if(!running.add(id)){slots.release();continue;}
            try{workers.execute(()->{try{dispatch(id);}catch(RuntimeException failed){try{awards.retry(id);}catch(RuntimeException unavailable){/* PENDING remains durable. */}}
                finally{running.remove(id);slots.release();}});}catch(RejectedExecutionException closing){running.remove(id);slots.release();}
        }
    }
    public void dispatch(long game){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Enter boundary before award transaction");
        long room=jdbc.queryForObject("select room_id from game_session where id=?",Long.class,game);
        rooms.read(RoomBoundary.Scope.room(room),()->{
            // Repair receipt commits separately, so a later policy/data failure retains retry diagnostics.
            if(!awards.exists(game))writes.seedMissing(game);
            writes.finalizeGame(game);return true;
        });
    }
    @PreDestroy public void close(){workers.shutdownNow();}
}
