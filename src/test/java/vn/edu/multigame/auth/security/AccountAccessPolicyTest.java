package vn.edu.multigame.auth.security;

import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import vn.edu.multigame.auth.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAccessPolicyTest {
    final AccountAccessPolicy policy=spy(new AccountAccessPolicy(mock(JdbcTemplate.class)));
    AccountAccessPolicy.State none(long user,long epoch){return new AccountAccessPolicy.State(user,"NONE",null,epoch,epoch,null);}
    @BeforeEach void available(){doReturn(none(1,0)).when(policy).state(1);doReturn(none(2,0)).when(policy).state(2);}
    void transaction(){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();}
    void finish(int status){TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(status));TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}
    @AfterEach void cleanup(){if(TransactionSynchronizationManager.isSynchronizationActive())finish(TransactionSynchronization.STATUS_ROLLED_BACK);}
    @Test void rollbackReopensProvisionalEpochFence(){
        transaction();policy.fenceForCommit(1,1);assertThat(policy.beginSend(1,0)).isFalse();finish(TransactionSynchronization.STATUS_ROLLED_BACK);assertThat(policy.beginSend(1,0)).isTrue();
    }
    @Test void issuedSendDoesNotBlockBanAndOtherAccountStillSends(){
        assertThat(policy.beginSend(1,0)).isTrue();transaction();policy.fenceForCommit(1,1);finish(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(policy.beginSend(1,0)).isFalse();assertThat(policy.beginSend(2,0)).isTrue();doReturn(none(1,1)).when(policy).state(1);assertThat(policy.beginSend(1,1)).isTrue();
    }
    @Test void durableEpochAfterUnbanStillRejectsEveryOlderSession(){
        doReturn(none(1,3)).when(policy).state(1);for(long old=0;old<3;old++){long epoch=old;assertThatThrownBy(()->policy.requireSession(1,epoch)).isInstanceOf(AuthFailure.class).hasMessageContaining("thu hồi");}assertThat(policy.beginSend(1,3)).isTrue();
    }
    @Test void oldRollbackCallbackCannotClearNewCommittedBanWithSameEpoch(){
        transaction();policy.fenceForCommit(1,1);var old=TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);
        // The DB rollback released its User lock; its completion callback has not run yet.
        transaction();policy.fenceForCommit(1,1);finish(TransactionSynchronization.STATUS_COMMITTED);
        old.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(policy.beginSend(1,0)).isFalse();
    }
    @Test void rollbackRemovesOnlyItsPublicationWhileNewTransactionIsPending(){
        transaction();policy.fenceForCommit(1,1);var old=TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);
        transaction();policy.fenceForCommit(1,1);old.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(policy.beginSend(1,0)).isFalse();finish(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(policy.beginSend(1,0)).isTrue();
    }
    @Test void staleAccountReadCannotStartSendAfterCommitFence()throws Exception{
        var read=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(c->{read.countDown();assertThat(release.await(3,TimeUnit.SECONDS)).isTrue();return none(1,0);}).when(policy).state(1);
        try(var worker=Executors.newSingleThreadExecutor()){
            var send=worker.submit(()->policy.beginSend(1,0));assertThat(read.await(3,TimeUnit.SECONDS)).isTrue();transaction();policy.fenceForCommit(1,1);finish(TransactionSynchronization.STATUS_COMMITTED);release.countDown();assertThat(send.get(3,TimeUnit.SECONDS)).isFalse();
        }finally{release.countDown();}
    }
}
