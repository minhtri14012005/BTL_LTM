package vn.edu.multigame.game.service;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

/** Durable per-game restriction plus a nonblocking send-start fence. No network I/O. */
@Service @Profile("mysql")
public class GameAccessPolicy {
    private record Key(long game,long user) {}
    private record State(boolean committed,Set<Object> pending,long issued) {
        boolean blocked(){return committed || !pending.isEmpty();}
    }
    private final ConcurrentHashMap<Key,AtomicReference<State>> fences=new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    public GameAccessPolicy(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean restricted(long game,long user){
        return jdbc.queryForObject("select (exists(select 1 from game_participation_restriction where game_session_id=? and user_id=?)) + (exists(select 1 from account_effect_intent where game_session_id=? and user_id=? and effect_type='GAME'))",Long.class,game,user,game,user)>0;
    }
    public Set<Long> restrictedUsers(long game){return Set.copyOf(jdbc.queryForList("select user_id from game_participation_restriction where game_session_id=? union select user_id from account_effect_intent where game_session_id=? and effect_type='GAME'",Long.class,game,game));}
    public void requireAllowed(long game,long user){if(restricted(game,user))throw new GameFailure(HttpStatus.FORBIDDEN,"GAME_KICKED");}
    public void requireMediaAllowed(long game,long user){
        if(jdbc.queryForObject("select count(*) from game_session where id=? and status='ACTIVE'",Long.class,game)>0 && restricted(game,user))
            throw new GameFailure(HttpStatus.FORBIDDEN,"GAME_KICKED");
    }
    /** Called inside the kick TX before COMMIT. Rollback restores the provisional fence.
     * COMMIT never waits for a socket already sending; a permit CAS is that frame's
     * send-start linearization point. Frames that have not claimed it must drop. */
    public void fenceForCommit(long game,long user){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Kick requires a transaction");
        var gate=fences.computeIfAbsent(new Key(game,user),k->new AtomicReference<>(new State(false,Set.of(),0)));
        Object publication=new Object();
        gate.updateAndGet(s->{var pending=new HashSet<>(s.pending());pending.add(publication);return new State(s.committed(),Set.copyOf(pending),s.issued());});
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){gate.updateAndGet(s->{
                var pending=new HashSet<>(s.pending());pending.remove(publication);
                return new State(s.committed() || status==STATUS_COMMITTED,Set.copyOf(pending),s.issued());
            });}
        });
    }
    /** Writer only: fresh DB check, then atomic permission to START one send.
     * No lock is held during socket.sendMessage; Kick can fence while I/O stalls. */
    public boolean beginSend(long game,long user){
        if(restricted(game,user))return false;
        var gate=fences.computeIfAbsent(new Key(game,user),k->new AtomicReference<>(new State(false,Set.of(),0)));
        while(true){var state=gate.get();if(state.blocked())return false;if(gate.compareAndSet(state,new State(state.committed(),state.pending(),state.issued()+1)))return true;}
    }
    @Scheduled(fixedDelay=60_000) public void cleanup(){
        // FINISHED cannot be kicked again. Durable checks still protect retained snapshots.
        for(long game:fences.keySet().stream().mapToLong(Key::game).distinct().toArray())
            if(jdbc.queryForObject("select count(*) from game_session where id=? and status='ACTIVE'",Long.class,game)==0)
                fences.keySet().removeIf(k->k.game()==game);
    }
}
