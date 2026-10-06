package vn.edu.quiz.game.service;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.realtime.message.command.GameCommand;
import vn.edu.quiz.realtime.session.SessionQueue;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real raw WS/cookie/MySQL. Clock/latches control races; expected scores come from Overview. */
class GameCommandNetworkIT extends GameNetworkFixture {
    @Test void concurrentSameIdAndDifferentIdAnswerReplayBeforeClosePhaseAndMismatch() throws Exception {
        var f=fixture(); var a=new Wire(f.roster().getFirst()); var duplicate=a; var b=new Wire(f.roster().get(1));
        long id=start(f); open(id,1); var original=command("ANSWER",id,1,Map.of("option","D"));
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            Callable<Void> first=() -> { ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier"); a.send(original); return null; };
            Callable<Void> second=() -> { ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier"); duplicate.send(original); return null; };
            var one=pool.submit(first); var two=pool.submit(second); assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown(); one.get(5,TimeUnit.SECONDS);two.get(5,TimeUnit.SECONDS);
        }
        var ack=a.response(original.path("requestId").asText(),1); accepted(ack);
        assertThat(duplicate.response(original.path("requestId").asText(),2)).isEqualTo(ack); assertThat(answerCount(id)).isEqualTo(1);
        assertThat(ack.toString()).doesNotContain("correctAnswer","CORRECT","WRONG","outcome","scoreDelta");
        rejected(a.response(command("ANSWER",id,1,Map.of("option","A"))),"ALREADY_ANSWERED");
        // User is part of scope: a different Player can use the same request UUID independently.
        accepted(b.response(original)); assertThat(answerCount(id)).isEqualTo(2);
        expire(observer.next("QUESTION_START",id,1)); observer.next("DECISION_STARTED",id,2);
        a.send(original); assertThat(a.response(original.path("requestId").asText(),3)).isEqualTo(ack);
        var changedIndex=original.deepCopy().put("questionIndex",2); rejected(a.response(changedIndex),"INVALID_REQUEST_ID");
        var changedType=original.deepCopy().put("type","USE_SPIN"); changedType.set("payload",json.createObjectNode());
        a.send(changedType); rejected(a.response(original.path("requestId").asText(),5),"INVALID_REQUEST_ID");
        assertThat(answerCount(id)).isEqualTo(3); // Two accepted, one NO_ANSWER, never a second valid Answer.
    }

    @Test void fullWsTenQuestionsResourcesResultEffectsEndRoomUpdateAndFinishedRetention() throws Exception {
        var f=fixture(); var host=new Wire(f.host()); var players=new ArrayList<Wire>();
        host.subscribe(f); for(var account:f.roster()) { var wire=new Wire(account); wire.subscribe(f); players.add(wire); }
        doReturn(SpinEffect.BONUS,SpinEffect.SAFE).when(spins).select(any(),any());
        var startCommand=envelope("START_GAME","ROOM",f.room().id(),null,Map.of("revision",f.room().revision(),"questionCount",10));
        var started=host.response(startCommand); accepted(started); long id=started.path("payload").path("gameSessionId").asLong(); gameIds.add(id);installed.add(id);
        observer.next("DECISION_STARTED",id,1); var a=players.get(0); var b=players.get(1); var c=players.get(2);
        var spin=command("USE_SPIN",id,1,Map.of()); var spinAck=a.response(spin); accepted(spinAck);
        assertThat(spinAck.path("payload").path("spinEffect").asText()).isEqualTo("BONUS");
        assertThat(spinAck.path("payload").path("remainingSpins").asInt()).isZero();
        var star=command("USE_STAR",id,1,Map.of()); var starAck=a.response(star); accepted(starAck);
        accepted(b.response(command("USE_SPIN",id,1,Map.of()))); accepted(c.response(command("USE_STAR",id,1,Map.of())));
        rejected(c.response(command("USE_SPIN",id,1,Map.of())),"SPIN_AFTER_STAR");
        var firstAnswer=command("ANSWER",id,1,Map.of("option","D")); JsonNode firstAck=null;
        for(int index=1;index<=10;index++) {
            open(id,index); scheduler.advance(clock.mono.get()+10);
            for(int player=0;player<3;player++) {
                var answer=index==1 && player==0?firstAnswer:command("ANSWER",id,index,Map.of("option","D"));
                var ack=players.get(player).response(answer); accepted(ack); if(index==1 && player==0) firstAck=ack;
            }
            var result=a.event("QUESTION_RESULT",index); assertThat(result.path("questionIndex").asInt()).isEqualTo(index);
            assertThat(result.path("payload").path("player").path("userId").asLong()).isEqualTo(f.roster().getFirst().id());
            var self=result.path("payload").path("results").get(0);
            assertThat(self.path("outcome").asText()).isEqualTo("CORRECT");
            assertThat(self.path("winStreak").asInt()).isEqualTo(index%5);
            assertThat(self.path("momentumGranted").asBoolean()).isEqualTo(index==5 || index==10);
            assertThat(self.path("momentumConsumed").asBoolean()).isEqualTo(index==6);
            assertThat(result.path("payload").path("members").toString()).doesNotContain("remainingSpinPool","selectedOption");
            a.event("LEADERBOARD_UPDATED",index);
        }
        var ended=a.event("GAME_END",10); assertThat(ended.path("payload").path("endReason").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(143,121,138);
        assertThat(ended.path("payload").path("winners").get(0).asLong()).isEqualTo(f.roster().getFirst().id());
        assertThat(host.event("QUESTION_RESULT",10).path("payload").path("player").isNull()).isTrue();
        a.next(m -> m.path("type").asText().equals("ROOM_UPDATED") && m.path("payload").path("status").asText().equals("WAITING"));
        assertThat(answerCount(id)).isEqualTo(30);
        a.send(spin); assertThat(a.response(spin.path("requestId").asText(),2)).isEqualTo(spinAck);
        a.send(star); assertThat(a.response(star.path("requestId").asText(),2)).isEqualTo(starAck);
        a.send(firstAnswer); assertThat(a.response(firstAnswer.path("requestId").asText(),2)).isEqualTo(firstAck);
        rejected(a.response(command("ANSWER",id,10,Map.of("option","D"))),"QUESTION_CLOSED");
        assertThat(host.response(startCommand)).isEqualTo(started); // Room receipt also survives End.
        verify(spins,times(2)).select(any(),any());
        long finishMono=ended.path("serverTimeMs").asLong()-clock.epoch;
        scheduler.advance(finishMono+SessionQueue.RETENTION_MS-1); a.send(spin); assertThat(a.response(spin.path("requestId").asText(),3)).isEqualTo(spinAck);
        scheduler.advance(finishMono+SessionQueue.RETENTION_MS); runtime.cleanup();
        a.send(spin); rejected(a.response(spin.path("requestId").asText(),4),"SERVICE_UNAVAILABLE");
        assertThat(runtime.snapshot(id,f.roster().getFirst().id()).status()).isEqualTo(GameStatus.FINISHED);
    }

    @Test void hardshipSpectatorOutsiderClientIdentityAndEliminatedPermission() throws Exception {
        var f=fixture(); var player=new Wire(f.roster().getFirst()); var other=new Wire(f.roster().get(1)); var third=new Wire(f.roster().getLast()); var host=new Wire(f.host()); var outsider=new Wire(account());
        long id=start(f); doReturn(SpinEffect.HARDSHIP).when(spins).select(any(),any()); var spin=command("USE_SPIN",id,1,Map.of()); var original=player.response(spin); accepted(original);
        rejected(player.response(command("USE_STAR",id,1,Map.of())),"STAR_FORBIDDEN_HARDSHIP");
        assertThat(jdbc.queryForObject("select star_available from player_session where game_session_id=? and user_id=?",Boolean.class,id,f.roster().getFirst().id())).isTrue();
        rejected(host.response(command("USE_SPIN",id,1,Map.of())),"FORBIDDEN"); rejected(outsider.response(command("USE_SPIN",id,1,Map.of())),"FORBIDDEN");
        var forged=command("ANSWER",id,1,Map.of("option","D")).put("userId",f.roster().getLast().id()); player.send(forged);
        player.next(m -> m.path("kind").asText().equals("ERROR") && m.path("code").asText().equals("INVALID_MESSAGE")); assertThat(answerCount(id)).isZero();
        jdbc.update("update player_session set score=0 where game_session_id=? and user_id=?",id,f.roster().getFirst().id()); // Valid persisted fixture to trigger elimination.
        open(id,1); accepted(player.response(command("ANSWER",id,1,Map.of("option","A")))); accepted(other.response(command("ANSWER",id,1,Map.of("option","D")))); accepted(third.response(command("ANSWER",id,1,Map.of("option","D"))));
        var eliminated=player.event("PLAYER_ELIMINATED",1); assertThat(eliminated.path("payload").path("player").path("state").asText()).isEqualTo("ELIMINATED");
        assertThat(eliminated.path("payload").path("results").get(0).path("eliminatedQuestionIndex").asInt()).isEqualTo(1);
        observer.next("DECISION_STARTED",id,2);
        rejected(player.response(command("ANSWER",id,2,Map.of("option","D"))),"FORBIDDEN"); rejected(player.response(command("USE_SPIN",id,2,Map.of())),"FORBIDDEN"); rejected(player.response(command("USE_STAR",id,2,Map.of())),"FORBIDDEN");
        player.send(spin); assertThat(player.response(spin.path("requestId").asText(),2)).isEqualTo(original);
        var otherType=spin.deepCopy().put("type","USE_STAR"); player.send(otherType);
        rejected(player.response(spin.path("requestId").asText(),3),"INVALID_REQUEST_ID");
        verify(spins,times(1)).select(any(),any());
    }

    @Test void spinRollbackRetriesCapturedDrawThenConcurrentRetryReceivesSameCommittedAck() throws Exception {
        var f=fixture(); var player=new Wire(f.roster().getFirst()); long id=start(f); AtomicInteger attempts=new AtomicInteger();
        doReturn(SpinEffect.BREAKTHROUGH).when(spins).select(any(),any());
        doAnswer(inv -> { var result=inv.callRealMethod(); if(attempts.incrementAndGet()<=2) throw new TransientDataAccessResourceException("Injected rollback after Spin SQL"); return result; }).when(transactions).useSpin(eq(id),eq(f.roster().getFirst().id()),eq(1),eq(SpinEffect.BREAKTHROUGH));
        var spin=command("USE_SPIN",id,1,Map.of()); player.send(spin);
        waitUntil(() -> scheduler.tasks.stream().anyMatch(task -> !task.cancelled().get() && task.delay()==100));
        assertThat(jdbc.queryForObject("select remaining_spins from player_session where game_session_id=? and user_id=?",Integer.class,id,f.roster().getFirst().id())).isEqualTo(1);
        assertThat(player.messages.stream().filter(m -> m.path("kind").asText().equals("ACK") && m.path("requestId").asText().equals(spin.path("requestId").asText()))).isEmpty();
        player.send(spin); scheduler.retry(100); scheduler.retry(300);
        var ack=player.response(spin.path("requestId").asText(),1); accepted(ack); assertThat(player.response(spin.path("requestId").asText(),2)).isEqualTo(ack);
        assertThat(attempts.get()).isEqualTo(3); verify(spins,times(1)).select(any(),any());
        assertThat(ack.path("payload").path("spinEffect").asText()).isEqualTo("BREAKTHROUGH");
        assertThat(jdbc.queryForObject("select remaining_spins from player_session where game_session_id=? and user_id=?",Integer.class,id,f.roster().getFirst().id())).isZero();
    }
    @Test void exhaustedSpinRollbackNeverCachesAcceptOrConsumesResource() throws Exception {
        var f=fixture(); var player=new Wire(f.roster().getFirst()); long id=start(f);
        doReturn(SpinEffect.BONUS).when(spins).select(any(),any());
        doAnswer(inv -> { inv.callRealMethod(); throw new TransientDataAccessResourceException("Injected rollback after Spin SQL"); }).when(transactions).useSpin(eq(id),anyLong(),eq(1),eq(SpinEffect.BONUS));
        var spin=command("USE_SPIN",id,1,Map.of()); player.send(spin); scheduler.retry(100); scheduler.retry(300);
        rejected(player.response(spin.path("requestId").asText(),1),"SERVICE_UNAVAILABLE"); observer.next("GAME_END",id,1);
        assertThat(jdbc.queryForObject("select remaining_spins from player_session where game_session_id=? and user_id=?",Integer.class,id,f.roster().getFirst().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_spin from player_session where game_session_id=? and user_id=?",String.class,id,f.roster().getFirst().id())).isNull();
        player.send(spin); rejected(player.response(spin.path("requestId").asText(),2),"SERVICE_UNAVAILABLE");
        verify(spins,times(1)).select(any(),any());
    }
    @Test void answerRollbackDoesNotCacheBeforeCommitAndRetryAfterScoringReturnsAcceptanceOnly() throws Exception {
        var f=fixture(); var player=new Wire(f.roster().getFirst()); long id=start(f); open(id,1); AtomicInteger attempts=new AtomicInteger();
        doAnswer(inv -> { var result=inv.callRealMethod(); if(attempts.incrementAndGet()<=2) throw new TransientDataAccessResourceException("Injected Answer rollback after SQL"); return result; }).when(transactions).accept(eq(id),eq(f.roster().getFirst().id()),eq(1),any(),anyLong(),anyLong());
        var answer=command("ANSWER",id,1,Map.of("option","D")); player.send(answer);
        waitUntil(() -> scheduler.tasks.stream().anyMatch(task -> !task.cancelled().get() && task.delay()==100)); assertThat(answerCount(id)).isZero();
        scheduler.retry(100); scheduler.retry(300); var ack=player.response(answer.path("requestId").asText(),1); accepted(ack); assertThat(answerCount(id)).isEqualTo(1);
        expire(observer.next("QUESTION_START",id,1)); observer.next("QUESTION_RESULT",id,1);
        player.send(answer); assertThat(player.response(answer.path("requestId").asText(),2)).isEqualTo(ack); assertThat(ack.toString()).doesNotContain("CORRECT","correctAnswer","scoreDelta");
        assertThat(attempts.get()).isEqualTo(3); assertThat(jdbc.queryForObject("select score from player_session where game_session_id=? and user_id=?",Integer.class,id,f.roster().getFirst().id())).isEqualTo(30);
    }
    @Test void twoDifferentIdsRaceForOneAnswerAndExactlyOneIsAccepted() throws Exception {
        var f=fixture(); var a=new Wire(f.roster().getFirst()); var b=a; long id=start(f); open(id,1);
        var first=command("ANSWER",id,1,Map.of("option","D")); var second=command("ANSWER",id,1,Map.of("option","A"));
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            var one=pool.submit(() -> { ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier"); a.send(first); return null; });
            var two=pool.submit(() -> { ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier"); b.send(second); return null; });
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown(); one.get(5,TimeUnit.SECONDS);two.get(5,TimeUnit.SECONDS);
        }
        var one=a.response(first.path("requestId").asText(),1); var two=b.response(second.path("requestId").asText(),1);
        assertThat(List.of(one,two).stream().filter(m -> m.path("kind").asText().equals("ACK"))).hasSize(1);
        assertThat(List.of(one,two).stream().filter(m -> m.path("code").asText().equals("ALREADY_ANSWERED"))).hasSize(1);
        assertThat(answerCount(id)).isEqualTo(1);
    }
    @Test void queuedCachedReplayRechecksAuthenticationAfterLogout() throws Exception {
        var f=fixture(); var blocked=new Wire(f.roster().getFirst()); var loggedOut=new Wire(f.roster().get(1)); long id=start(f); open(id,1);
        var original=command("ANSWER",id,1,Map.of("option","D")); accepted(loggedOut.response(original));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),replayIngress=new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("Blocked processor"); return inv.callRealMethod(); })
                .when(transactions).accept(eq(id),eq(f.roster().getFirst().id()),eq(1),any(),anyLong(),anyLong());
        doAnswer(inv -> { var result=inv.callRealMethod(); replayIngress.countDown(); return result; })
                .when(runtime).command(eq(f.roster().get(1).id()),argThat((GameCommand c) -> c.requestId().equals(original.path("requestId").asText())),any(),any());
        var pending=command("ANSWER",id,1,Map.of("option","D")); blocked.send(pending);
        try {
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue(); loggedOut.send(original);
            assertThat(replayIngress.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(loggedOut.client.call("POST","/api/auth/logout",null).statusCode()).isEqualTo(204);
            assertThat(loggedOut.closed.get(5,TimeUnit.SECONDS)).isEqualTo(4001); release.countDown();
            accepted(blocked.response(pending.path("requestId").asText(),1));
            assertThat(loggedOut.messages.stream().filter(m -> m.path("kind").asText().equals("ACK") && m.path("requestId").asText().equals(original.path("requestId").asText()))).hasSize(1);
            assertThat(answerCount(id)).isEqualTo(2);
        } finally { release.countDown(); }
    }
    @Test void twoSpinsUseSeparateRemainingEffectsAndGameScopesDoNotShareReceipts() throws Exception {
        var host=account(); var roster=List.of(account(),account(),account()); long quiz=quiz(host,20); var room=room(host,quiz,roster); var f=new Fixture(host,roster,quiz,room); var player=new Wire(roster.getFirst());
        var started=operations.start(host.auth(),room.id(),new vn.edu.quiz.game.dto.request.StartGameRequest(UUID.randomUUID().toString(),room.revision(),20),() -> {});
        long id=started.gameSessionId();gameIds.add(id);installed.add(id);observer.next("DECISION_STARTED",id,1);
        doReturn(SpinEffect.BONUS,SpinEffect.SAFE,SpinEffect.BREAKTHROUGH).when(spins).select(any(),any());
        var first=command("USE_SPIN",id,1,Map.of()); accepted(player.response(first));
        var pool=runtime.snapshot(id,roster.getFirst().id()).player(); assertThat(pool.remainingSpins()).isEqualTo(1);assertThat(pool.remainingSpinPool()).doesNotContain(SpinEffect.BONUS);
        assertThat(runtime.snapshot(id,roster.get(1).id()).player().remainingSpinPool()).hasSize(6);
        open(id,1); expire(observer.next("QUESTION_START",id,1));observer.next("DECISION_STARTED",id,2);
        var second=player.response(command("USE_SPIN",id,2,Map.of())); accepted(second);
        assertThat(second.path("payload").path("remainingSpins").asInt()).isZero();assertThat(runtime.snapshot(id,roster.getFirst().id()).player().remainingSpinPool()).doesNotContain(SpinEffect.BONUS,SpinEffect.SAFE);
        runtime.interrupt(id).get(5,TimeUnit.SECONDS); observer.next("GAME_END",id,2);
        var current=rooms.get(host.id(),room.id());var next=operations.start(host.auth(),room.id(),request(current),() -> {}); long other=next.gameSessionId();gameIds.add(other);installed.add(other);observer.next("DECISION_STARTED",other,1);
        var another=first.deepCopy(); another.set("target",json.valueToTree(Map.of("kind","GAME","id",other)));
        var fresh=player.response(another);accepted(fresh);assertThat(fresh.path("target").path("id").asLong()).isEqualTo(other);assertThat(fresh.path("payload").path("spinEffect").asText()).isEqualTo("BREAKTHROUGH");
    }
    @Test void replayBypassesNewEffectAdmissionButNeverBypassesIdentityValidation() throws Exception {
        var f=fixture();var wire=new Wire(f.roster().getFirst());long id=start(f);doReturn(SpinEffect.BONUS).when(spins).select(any(),any());
        var request=command("USE_SPIN",id,1,Map.of());var original=wire.response(request);accepted(original);
        var parsed=new GameCommand(1,"COMMAND",request.path("requestId").asText(),"USE_SPIN",vn.edu.quiz.realtime.message.common.GameTarget.of(id),1,request.get("payload"));
        var replay=runtime.command(f.roster().getFirst().id(),parsed,() -> {},() -> {throw new vn.edu.quiz.room.service.RoomFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"SUBSCRIPTION_LIMIT_REACHED");}).get(5,TimeUnit.SECONDS);
        assertThat(json.writeValueAsString(replay)).isEqualTo(original.toString());
        assertThatThrownBy(() -> runtime.command(f.roster().getFirst().id(),parsed,() -> {throw new GameFailure(org.springframework.http.HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");},() -> {}).get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(GameFailure.class);
        verify(spins,times(1)).select(any(),any());
    }
    @Test void actualWsIngressEnforcesStrictDecisionAndAnswerDeadlinesWithoutWaitingForTimer() throws Exception {
        var f=fixture();var a=new Wire(f.roster().getFirst());var b=new Wire(f.roster().get(1));var c=new Wire(f.roster().getLast());long id=start(f);doReturn(SpinEffect.BONUS).when(spins).select(any(),any());
        var decision=observer.next("DECISION_STARTED",id,1);long decisionDeadline=decision.deadlineEpochMs()-clock.epoch;
        clock.mono.set(decisionDeadline-1);accepted(a.response(command("USE_SPIN",id,1,Map.of())));
        clock.mono.set(decisionDeadline);rejected(b.response(command("USE_STAR",id,1,Map.of())),"DECISION_CLOSED");
        clock.mono.incrementAndGet();rejected(c.response(command("USE_SPIN",id,1,Map.of())),"DECISION_CLOSED");
        scheduler.advance(clock.mono.get());var opened=observer.next("QUESTION_START",id,1);
        assertThat(opened.remainingMs()).isEqualTo(1000L);
        long answerDeadline=opened.deadlineEpochMs()-clock.epoch;
        clock.mono.set(answerDeadline-1);accepted(a.response(command("ANSWER",id,1,Map.of("option","D"))));
        clock.mono.set(answerDeadline);rejected(b.response(command("ANSWER",id,1,Map.of("option","D"))),"QUESTION_CLOSED");
        clock.mono.incrementAndGet();rejected(c.response(command("ANSWER",id,1,Map.of("option","D"))),"QUESTION_CLOSED");
        scheduler.advance(clock.mono.get());observer.next("QUESTION_RESULT",id,1);
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(35,19,19);
        verify(spins,times(1)).select(any(),any());
    }
}
