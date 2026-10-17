package vn.edu.multigame.game.service;

import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Unit proof of send-start ordering. Real transaction/marker tests live in Kick IT. */
class GameAccessPolicyTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final GameAccessPolicy policy=new GameAccessPolicy(jdbc);
    @BeforeEach void setup(){when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);}
    void transaction(){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();}
    void finish(int status){TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(status));TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}
    @AfterEach void cleanup(){if(TransactionSynchronizationManager.isSynchronizationActive())finish(TransactionSynchronization.STATUS_ROLLED_BACK);}
    @Test void rollbackRestoresProvisionalFenceAndCommitLeavesItClosed(){
        transaction();policy.fenceForCommit(10,1);assertThat(policy.beginSend(10,1)).isFalse();assertThat(policy.beginSend(11,1)).isTrue();finish(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(policy.beginSend(10,1)).isTrue();transaction();policy.fenceForCommit(10,1);finish(TransactionSynchronization.STATUS_COMMITTED);assertThat(policy.beginSend(10,1)).isFalse();
    }
    @Test void durableRestrictionStillDeniesAfterNewPolicyInstance(){
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
        var afterRestart=new GameAccessPolicy(jdbc);assertThat(afterRestart.beginSend(10,1)).isFalse();assertThatThrownBy(()->afterRestart.requireAllowed(10,1)).isInstanceOf(GameFailure.class).hasMessage("GAME_KICKED");
    }
    @Test void staleDbReadCannotClaimSendStartAfterFence()throws Exception{
        var read=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(c->{read.countDown();assertThat(release.await(3,TimeUnit.SECONDS)).isTrue();return 0L;});
        try(var worker=Executors.newSingleThreadExecutor()){
            var pending=worker.submit(()->policy.beginSend(10,1));assertThat(read.await(3,TimeUnit.SECONDS)).isTrue();
            transaction();policy.fenceForCommit(10,1);finish(TransactionSynchronization.STATUS_COMMITTED);release.countDown();assertThat(pending.get(3,TimeUnit.SECONDS)).isFalse();
        }finally{release.countDown();}
    }
    @Test void preFenceSendPermitDoesNotBlockKickOrPermitLaterFrames(){
        assertThat(policy.beginSend(10,1)).isTrue();transaction();policy.fenceForCommit(10,1);finish(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(policy.beginSend(10,1)).isFalse();assertThat(policy.beginSend(10,2)).isTrue();assertThat(policy.beginSend(11,1)).isTrue();
    }
    @Test void kickFenceRequiresTransaction(){assertThatThrownBy(()->policy.fenceForCommit(10,1)).isInstanceOf(IllegalStateException.class);}
    @Test void oldRollbackCallbackCannotClearNewCommittedRestriction(){
        transaction();policy.fenceForCommit(10,1);var old=TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);
        transaction();policy.fenceForCommit(10,1);finish(TransactionSynchronization.STATUS_COMMITTED);
        old.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(policy.beginSend(10,1)).isFalse();
    }
    @Test void rollbackRemovesOnlyItsPublicationWhileNewRestrictionIsPending(){
        transaction();policy.fenceForCommit(10,1);var old=TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);
        transaction();policy.fenceForCommit(10,1);old.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(policy.beginSend(10,1)).isFalse();finish(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(policy.beginSend(10,1)).isTrue();
    }
}
