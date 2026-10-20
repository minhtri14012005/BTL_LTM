package vn.edu.multigame.game.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.multigame.achievements.service.AwardReconciler;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Deterministic test-harness regression: a retained context has a real scheduled worker. */
class G6AwardCleanupIsolationIT extends AdvancedAwardFixture {
    @MockitoSpyBean JdbcTemplate observedJdbc;
    boolean cleaned;

    @Override @AfterEach void cleanup() {
        if (!cleaned) { super.cleanup(); cleaned=true; }
    }

    @Test void retainedContextCannotReseedAwardDecisionDuringFixtureCleanup() throws Exception {
        // This context has the inherited paused spy. A distinct cached context does not.
        var retainedManager=new TestContextManager(GameCommandNetworkIT.class);
        var retained=retainedManager.getTestContext().getApplicationContext();
        var foreign=retained.getBean(AwardReconciler.class);
        assertThat(foreign).isNotSameAs(awardReconciler);
        var target=new AtomicLong(-1);
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var completed=new CountDownLatch(1);
        var thread=new AtomicReference<String>();
        var deletedOnce=new AtomicBoolean();
        var transactional=new AtomicBoolean();
        doAnswer(call -> {
            if (((Long)call.getArgument(0)).longValue()!=target.get())return call.callRealMethod();
            thread.set(Thread.currentThread().getName());entered.countDown();
            assertThat(release.await(10,TimeUnit.SECONDS)).as("release retained scheduled worker").isTrue();
            try { return call.callRealMethod(); }
            finally { completed.countDown(); }
        }).when(foreign).dispatch(anyLong());
        try {
            var match=match(List.of(vn.edu.multigame.game.enums.GameMode.RIDDLE),List.of(1));
            target.set(match.game());
            question(match,1,Set.of(match.ids().getFirst()),Set.of());
            // No direct reconcile() call: the retained context's actual @Scheduled callback runs.
            assertThat(entered.await(10,TimeUnit.SECONDS)).as("foreign @Scheduled worker reached target").isTrue();
            assertThat(thread.get()).startsWith("award-");
            doAnswer(call -> {
                var result=call.callRealMethod();
                if (deletedOnce.compareAndSet(false,true)) {
                    transactional.set(TransactionSynchronizationManager.isActualTransactionActive());
                    System.out.println("G6_RACE: target="+target.get()+", sourceContext="+retained.getId()+
                            ", foreignWorker="+thread.get()+", cleanupTransaction="+transactional.get());
                    release.countDown();
                    if (!transactional.get()) {
                        // Reproduce the original unsafe interleaving exactly, without random sleeps.
                        assertThat(completed.await(10,TimeUnit.SECONDS)).isTrue();
                        assertThat(jdbc.queryForObject("select count(*) from game_award_decision where game_session_id=?",Integer.class,target.get())).isEqualTo(1);
                        System.out.println("G6_RACE: foreign worker re-created decision after fixture deleted it");
                    }
                }
                return result;
            }).when(observedJdbc).update(eq("delete from game_member where game_session_id=?"),eq(match.game()));
            assertThatCode(this::cleanup).as("fixture cleanup must fence the real foreign worker").doesNotThrowAnyException();
            assertThat(completed.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(transactional.get()).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from game_session where id=?",Integer.class,target.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from game_award_decision where game_session_id=?",Integer.class,target.get())).isZero();
            System.out.println("G6_RACE: cleanup committed, foreign worker completed, no parent/decision remains");
        } finally {
            release.countDown();
            doNothing().when(foreign).reconcile();
            retainedManager.getTestContext().markApplicationContextDirty(HierarchyMode.CURRENT_LEVEL);
        }
    }

    @Test void failedCleanupStillClosesOwnedHttpClients() {
        var user=account();
        var client=mock(Client.class);clients.add(client);
        var once=new AtomicBoolean();
        doAnswer(call -> {
            if(once.compareAndSet(false,true))throw new DataAccessResourceFailureException("G6_INJECTED_CLEANUP_FAILURE");
            return call.callRealMethod();
        }).when(observedJdbc).update(eq("delete from account_effect_intent where user_id=?"),eq(user.id()));
        assertThatThrownBy(this::cleanup).isInstanceOf(DataAccessResourceFailureException.class).hasMessage("G6_INJECTED_CLEANUP_FAILURE");
        verify(client).close();
        System.out.println("G6_RESOURCE: client closed despite injected SQL teardown exception");
    }
}
