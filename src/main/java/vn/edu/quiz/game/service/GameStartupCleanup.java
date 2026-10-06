package vn.edu.quiz.game.service;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
@Component @Profile("mysql") @Order(-10)
public class GameStartupCleanup implements ApplicationRunner {
    private final GameTransactions transactions;
    private volatile boolean ready;
    public GameStartupCleanup(GameTransactions transactions) { this.transactions=transactions; }
    public boolean ready() { return ready; }
    /** Compensation for a committed Start which could not enter the session processor.
     * Runs on the Start request thread; an uncertain Start transaction itself is never retried. */
    public CommittedGame interruptCommittedStart(long gameId,long epochMs) {
        for(int attempt=1;attempt<=3;attempt++) {
            try { return transactions.interrupt(gameId,epochMs); }
            catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
                if(attempt==3) throw GameFailure.unavailable();
                backoff(attempt);
            }
        }
        throw GameFailure.unavailable();
    }
    @Override public void run(ApplicationArguments arguments) {
        for(int attempt=1;attempt<=3;attempt++) {
            try { transactions.cleanupAbandoned(System.currentTimeMillis()); ready=true; return; }
            catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
                if(attempt==3) throw new IllegalStateException("Game startup cleanup did not commit; Start is disabled",failure);
                backoff(attempt);
            }
        }
    }
    private void backoff(int attempt) {
        try { Thread.sleep(attempt==1?100:300); } // Startup/request thread only, never a session worker.
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
