package vn.edu.multigame.game.service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.*;
import vn.edu.multigame.game.enums.GameMode;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.realtime.message.command.GameCommand;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real MySQL/HTTP/raw WS. Shared fake monotonic clock and barriers control timing, not gameplay. */
class MultimodeLifecycleIT extends GameNetworkFixture {
    Fixture fixture2(int quizCount,int riddleCount) {
        var host=account();var author=account();var roster=List.of(account(),account(),account());
        var plan=new ArrayList<RoomStageRequest>();long q=0;
        if(quizCount>0) {q=quiz(author,quizCount);plan.add(new RoomStageRequest(GameMode.QUIZ,q,quizCount,1000L));}
        if(riddleCount>0) {
            var item=new QuestionRequest("Có chân mà không đi?",null,null,null,List.of("cái bàn","bàn"));
            var riddle=quizService.create(host.auth(),new CreateQuestionBankRequest("Đố mẹo",Visibility.PRIVATE,Collections.nCopies(riddleCount,item),GameMode.RIDDLE));
            quizIds.add(riddle.id());plan.add(new RoomStageRequest(GameMode.RIDDLE,riddle.id(),riddleCount,1000L));
        }
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"V2",10,null,Participation.SPECTATOR,plan))).snapshot();roomIds.add(r.id());
        long rev=r.revision();roomOps.execute(host.auth(),r.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",rev),null,()->{},()->rooms.open(host.id(),r.id(),rev),(a,b)->{});
        for(var p:roster) roomOps.execute(p.auth(),r.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),()->{},()->rooms.join(p.id(),r.id(),r.roomCode(),Participation.PLAYER),(a,b)->{});
        return new Fixture(host,roster,q,rooms.get(host.id(),r.id()));
    }
    long start2(Fixture f) throws Exception {
        var host=new Client(f.host());int count=f.room().stages().stream().mapToInt(s->s.questionCount()).sum();
        var response=host.call("POST","/api/rooms/"+f.room().id()+"/start",new StartGameRequest(UUID.randomUUID().toString(),f.room().revision(),count));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);long id=json.readTree(response.body()).path("gameSessionId").asLong();
        gameIds.add(id);installed.add(id);observer.next("INTRO_STARTED",id,1);return id;
    }
    List<Wire> online(Fixture f) throws Exception {var list=new ArrayList<Wire>();for(var p:f.roster()){var w=new Wire(p);w.subscribe(f);list.add(w);}return list;}
    void ready(List<Wire> clients,long id,int index) throws Exception {for(var w:clients) accepted(w.response(command("CONTINUE",id,index,Map.of())));}
    void advance(GameSnapshot phase) {long due=phase.deadlineEpochMs()-clock.epoch;waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.due()==due));scheduler.advance(Math.max(due,clock.mono.get()));}
    GameSnapshot question2(long id,int index,boolean quizMode) {if(quizMode)advance(observer.next("DECISION_STARTED",id,index));return observer.next("QUESTION_START",id,index);}
    void nextAfterResult(long id,int index) {advance(observer.next("QUESTION_RESULT",id,index));}
    @Test void quizThenRiddleFullMatchSnapshotsResourcesHistoryAndTerminalReplay() throws Exception {
        var f=fixture2(10,2);var host=new Wire(f.host());host.subscribe(f);var ws=online(f);long id=start2(f);
        var intro=runtime.snapshot(id,f.host().id());assertThat(intro.player()).isNull();assertThat(intro.question()).isNull();assertThat(intro.remainingMs()).isEqualTo(10000);
        rejected(host.response(command("CONTINUE",id,1,Map.of())),"FORBIDDEN");ready(ws,id,1);
        doReturn(SpinEffect.BONUS).when(spins).select(any(),any());var spin=command("USE_SPIN",id,1,Map.of());var spinAck=ws.get(0).response(spin);accepted(spinAck);
        var star=command("USE_STAR",id,1,Map.of());accepted(ws.get(0).response(star));rejected(ws.get(0).response(command("USE_SPIN",id,1,Map.of())),"SPIN_AFTER_STAR");
        JsonNode first=null,firstAck=null;
        for(int i=1;i<=10;i++) {
            var opened=question2(id,i,true);assertThat(opened.question().correctAnswer()).isNull();assertThat(opened.schemaVersion()).isEqualTo(2);
            clock.mono.addAndGet(100);
            for(int p=0;p<3;p++){var cmd=command("ANSWER",id,i,Map.of("option",p==2 && i==1?"A":"D"));var ack=ws.get(p).response(cmd);accepted(ack);if(i==1 && p==0){first=cmd;firstAck=ack;}}
            observer.next("QUESTION_RESULT",id,i);nextAfterResult(id,i);
        }
        var stage=observer.next("INTRO_STARTED",id,11);assertThat(stage.stage().mode()).isEqualTo(GameMode.RIDDLE);assertThat(stage.question()).isNull();ready(ws,id,11);
        var riddle=question2(id,11,false);assertThat(riddle.question().options()).isEmpty();assertThat(riddle.question().payload()).isEmpty();
        var answer=command("ANSWER",id,11,Map.of("text","  CÁI   BÀN  "));var textAck=ws.get(0).response(answer);accepted(textAck);assertThat(textAck.toString()).doesNotContain("CORRECT","correctAnswer","acceptedAnswers");
        accepted(ws.get(1).response(command("ANSWER",id,11,Map.of("text","sai"))));rejected(ws.get(0).response(command("ANSWER",id,11,Map.of("text","bàn"))),"ALREADY_ANSWERED");
        rejected(ws.get(0).response(command("USE_STAR",id,11,Map.of())),"INVALID_STATE");advance(riddle);observer.next("QUESTION_RESULT",id,11);nextAfterResult(id,11);
        question2(id,12,false);clock.mono.addAndGet(200);for(var w:ws)accepted(w.response(command("ANSWER",id,12,Map.of("text","BÀN"))));
        var end=ws.get(0).event("GAME_END",12);assertThat(end.path("payload").path("endReason").asText()).isEqualTo("COMPLETED");assertThat(end.path("payload").path("hasOfficialWinner").asBoolean()).isTrue();
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(153,123,113);
        assertThat(jdbc.queryForList("select total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",Long.class,id)).containsExactly(1200L,1200L,1100L);
        assertThat(jdbc.queryForObject("select count(*) from player_session where game_session_id=? and player_state='ELIMINATED'",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select status from room where id=?",String.class,f.room().id())).isEqualTo("WAITING");
        assertThat(ws.get(0).response(first)).isEqualTo(firstAck);assertThat(ws.get(0).response(spin)).isEqualTo(spinAck);assertThat(ws.get(0).response(answer)).isEqualTo(textAck);verify(spins,times(1)).select(any(),any());
        var history=ws.get(0).client.call("GET","/api/games/history/"+id,null);assertThat(history.statusCode()).as(history.body()).isEqualTo(200);
        var detail=json.readTree(history.body());assertThat(detail.path("questions").size()).isEqualTo(12);assertThat(detail.path("questions").get(10).path("payload").path("acceptedAnswers").size()).isEqualTo(2);
        assertThat(detail.path("questions").get(10).path("answers").get(0).path("submittedAnswer").path("text").asText()).isEqualTo("  CÁI   BÀN  ");
        assertThat(json.readTree(ws.get(0).client.call("GET","/api/games/history",null).body()).path("items").toString()).contains("\"gameSessionId\":"+id);
    }
    @Test void introOfflineWaitsThenQuestionWaitSetKeepsDisconnectedUntilDeadlineButExcludesNext() throws Exception {
        var f=fixture2(0,2);var a=new Wire(f.roster().get(0));var b=new Wire(f.roster().get(1));long id=start2(f);
        ready(List.of(a,b),id,1);var intro=runtime.snapshot(id,f.host().id());assertThat(intro.phase()).isEqualTo(Phase.INTRO);advance(intro);
        var opened=question2(id,1,false);assertThat(opened.player()).isNull();accepted(a.response(command("ANSWER",id,1,Map.of("text","bàn"))));
        b.close();assertThat(b.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1000);assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        advance(opened);observer.next("QUESTION_RESULT",id,1);nextAfterResult(id,1);question2(id,2,false);
        accepted(a.response(command("ANSWER",id,2,Map.of("text","bàn"))));observer.next("GAME_END",id,2);
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(30,0,0);
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and answer_status='NO_ANSWER'",Integer.class,id)).isEqualTo(4);
    }
    @Test void emptyWaitSetWaitsDeadlineAndReconnectAnswerDoesNotGetExtraTime() throws Exception {
        var f=fixture2(0,1);long id=start2(f);advance(observer.next("INTRO_STARTED",id,1));var q=question2(id,1,false);
        assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);clock.mono.addAndGet(700);
        var a=new Wire(f.roster().get(0));var restored=a.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(restored);
        assertThat(restored.path("payload").path("remainingMs").asLong()).isEqualTo(300);assertThat(restored.path("payload").path("question").path("payload").size()).isZero();
        accepted(a.response(command("ANSWER",id,1,Map.of("text","bàn"))));assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        advance(q);observer.next("GAME_END",id,1);assertThat(jdbc.queryForObject("select total_correct_answer_time_ms from player_session where game_session_id=? and user_id=?",Long.class,id,f.roster().get(0).id())).isEqualTo(700);
    }
    @Test void typedDeadlineMinusOneEqualAndPlusOneWithoutTimerProcessing() throws Exception {
        var f=fixture2(0,1);var ws=online(f);long id=start2(f);ready(ws,id,1);var q=question2(id,1,false);long deadline=q.deadlineEpochMs()-clock.epoch;
        clock.mono.set(deadline-1);accepted(ws.get(0).response(command("ANSWER",id,1,Map.of("text","bàn"))));
        clock.mono.set(deadline);rejected(ws.get(1).response(command("ANSWER",id,1,Map.of("text","bàn"))),"QUESTION_CLOSED");
        clock.mono.incrementAndGet();rejected(ws.get(2).response(command("ANSWER",id,1,Map.of("text","bàn"))),"QUESTION_CLOSED");
        scheduler.advance(clock.mono.get());observer.next("GAME_END",id,1);assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and answer_status='CORRECT'",Integer.class,id)).isEqualTo(1);
    }
    @Test void readyBeforeTimerQueueSlowStillOpensExactlyOnceWithFreshDeadline() throws Exception {
        var f=fixture2(0,1);var ws=online(f);long id=start2(f);ready(ws.subList(0,2),id,1);var intro=runtime.snapshot(id,f.host().id());long deadline=intro.deadlineEpochMs()-clock.epoch;
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);doAnswer(inv->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Barrier");return inv.callRealMethod();}).when(transactions).ready(eq(id),eq(f.roster().get(2).id()),eq(1));
        var last=command("CONTINUE",id,1,Map.of());clock.mono.set(deadline-1);ws.get(2).send(last);
        try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();scheduler.advance(deadline+500);release.countDown();accepted(ws.get(2).response(last.path("requestId").asText(),1));var q=question2(id,1,false);assertThat(q.remainingMs()).isEqualTo(1000);assertThat(q.serverTimeMs()).isEqualTo(clock.epoch+deadline+500);
            assertThat(observer.seen.stream().filter(e->e.type().equals("QUESTION_START") && e.snapshot().gameSessionId()==id)).hasSize(1);
        }finally{release.countDown();}
    }
    @Test void textRollbackRetryCachesOnlyCommitAndScoringIsAtomic() throws Exception {
        var f=fixture2(0,1);var ws=online(f);long id=start2(f);ready(ws,id,1);question2(id,1,false);AtomicInteger attempts=new AtomicInteger();
        doAnswer(inv->{var result=inv.callRealMethod();if(attempts.incrementAndGet()==1)throw new TransientDataAccessResourceException("Injected text rollback");return result;}).when(transactions).acceptTyped(eq(id),eq(f.roster().get(0).id()),eq(1),isNull(),any(),anyLong(),anyLong());
        var cmd=command("ANSWER",id,1,Map.of("text","bàn"));ws.get(0).send(cmd);waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.delay()==100));assertThat(answerCount(id)).isZero();scheduler.retry(100);
        var ack=ws.get(0).response(cmd.path("requestId").asText(),1);accepted(ack);assertThat(ws.get(0).response(cmd)).isEqualTo(ack);accepted(ws.get(1).response(command("ANSWER",id,1,Map.of("text","bàn"))));accepted(ws.get(2).response(command("ANSWER",id,1,Map.of("text","bàn"))));
        observer.next("GAME_END",id,1);assertThat(answerCount(id)).isEqualTo(3);assertThat(attempts).hasValue(2);assertThat(observer.committed).doesNotContain(false);
    }
    @Test void answerIngressBeforeTimerWhileProcessorSlowIsCommittedOnce() throws Exception {
        var f=fixture2(0,1);var ws=online(f);long id=start2(f);ready(ws,id,1);var q=question2(id,1,false);
        long deadline=q.deadlineEpochMs()-clock.epoch;CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Barrier");return inv.callRealMethod();})
            .when(transactions).acceptTyped(eq(id),eq(f.roster().get(0).id()),eq(1),isNull(),any(),anyLong(),anyLong());
        clock.mono.set(deadline-1);var cmd=command("ANSWER",id,1,Map.of("text","bàn"));ws.get(0).send(cmd);
        try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();scheduler.advance(deadline+100);release.countDown();
            var ack=ws.get(0).response(cmd.path("requestId").asText(),1);accepted(ack);observer.next("GAME_END",id,1);
            assertThat(ws.get(0).response(cmd)).isEqualTo(ack);assertThat(answerCount(id)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select answer_time_ms from answer where game_session_id=? and answer_status='CORRECT'",Long.class,id)).isEqualTo(999);
        } finally {release.countDown();}
    }
    @Test void presenceIsCapturedAtWindowOpenRatherThanAfterCommit() throws Exception {
        var f=fixture2(0,1);var a=new Wire(f.roster().get(0));var b=new Wire(f.roster().get(1));long id=start2(f);
        accepted(a.response(command("CONTINUE",id,1,Map.of())));accepted(b.response(command("CONTINUE",id,1,Map.of())));
        CountDownLatch prepared=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(inv->{var result=inv.callRealMethod();prepared.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Barrier");return result;})
            .when(transactions).open(eq(id),eq(1),eq(Phase.QUESTION_OPEN),any());
        var intro=runtime.snapshot(id,f.host().id());long due=intro.deadlineEpochMs()-clock.epoch;
        try {advance(intro);assertThat(prepared.await(5,TimeUnit.SECONDS)).isTrue();b.close();b.closed.get(5,TimeUnit.SECONDS);release.countDown();
            var opened=question2(id,1,false);accepted(a.response(command("ANSWER",id,1,Map.of("text","bàn"))));
            assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
            advance(opened);observer.next("GAME_END",id,1);
        } finally {release.countDown();}
    }
    @Test void reconnectReplacementAcrossIntroDecisionOpenResultAndFinishedPreservesState() throws Exception {
        var f=fixture2(1,1);var ws=online(f);long id=start2(f);var initial=ws.get(0);
        var restored=initial.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(restored);GameSnapshotAssertions.snapshot(restored.path("payload"),f.roster().get(0).id());assertThat(restored.path("payload").path("phase").asText()).isEqualTo("INTRO");
        ready(ws,id,1);assertThat(runtime.snapshot(id,f.roster().get(0).id()).player().remainingSpins()).isZero();
        rejected(initial.response(command("USE_SPIN",id,1,Map.of())),"SPIN_NOT_AVAILABLE");
        var star=command("USE_STAR",id,1,Map.of());var starAck=initial.response(star);accepted(starAck);
        var replacement=new Wire(f.roster().get(0));assertThat(initial.closed.get(5,TimeUnit.SECONDS)).isEqualTo(4002);
        initial.next(m->m.path("type").asText().equals("SESSION_REPLACED"));
        var decision=replacement.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(decision);assertThat(decision.path("payload").path("player").path("starSelected").asBoolean()).isTrue();
        assertThat(replacement.response(star)).isEqualTo(starAck);var q=question2(id,1,true);clock.mono.addAndGet(100);
        var cmd=command("ANSWER",id,1,Map.of("option","D"));var ack=replacement.response(cmd);accepted(ack);
        var again=new Wire(f.roster().get(0));assertThat(replacement.closed.get(5,TimeUnit.SECONDS)).isEqualTo(4002);
        var open=again.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(open);assertThat(open.path("payload").path("player").path("alreadyAnswered").asBoolean()).isTrue();
        assertThat(open.path("payload").path("question").path("correctAnswer").isNull()).isTrue();assertThat(again.response(cmd)).isEqualTo(ack);
        accepted(ws.get(1).response(command("ANSWER",id,1,Map.of("option","D"))));accepted(ws.get(2).response(command("ANSWER",id,1,Map.of("option","D"))));
        observer.next("QUESTION_RESULT",id,1);var result=again.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(result);
        assertThat(result.path("payload").path("phase").asText()).isEqualTo("RESULT");assertThat(result.path("payload").path("results").size()).isEqualTo(3);
        again.close();again.closed.get(5,TimeUnit.SECONDS);nextAfterResult(id,1);advance(observer.next("INTRO_STARTED",id,2));var second=question2(id,2,false);
        var finalSocket=new Wire(f.roster().get(0));var current=finalSocket.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(current);GameSnapshotAssertions.snapshot(current.path("payload"),f.roster().get(0).id());
        assertThat(current.path("payload").path("questionIndex").asInt()).isEqualTo(2);assertThat(current.path("payload").path("player").path("score").asInt()).isEqualTo(25);
        assertThat(current.path("payload").path("player").path("starSelected").asBoolean()).isFalse();assertThat(current.path("payload").path("player").path("starAvailable").asBoolean()).isFalse();
        accepted(finalSocket.response(command("ANSWER",id,2,Map.of("text","bàn"))));accepted(ws.get(1).response(command("ANSWER",id,2,Map.of("text","bàn"))));accepted(ws.get(2).response(command("ANSWER",id,2,Map.of("text","bàn"))));
        observer.next("GAME_END",id,2);var terminal=finalSocket.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(terminal);GameSnapshotAssertions.snapshot(terminal.path("payload"),f.roster().get(0).id());assertThat(terminal.path("payload").path("phase").asText()).isEqualTo("FINISHED");assertThat(terminal.path("payload").path("player").path("score").asInt()).isEqualTo(45);
    }
    @Test void scoringRollbackRetriesWholeQuestionThenCancelObservesCommittedResult() throws Exception {
        var f=fixture2(0,2);var ws=online(f);long id=start2(f);ready(ws,id,1);question2(id,1,false);AtomicInteger attempts=new AtomicInteger();
        doAnswer(inv->{var scored=inv.callRealMethod();if(attempts.incrementAndGet()==1)throw new TransientDataAccessResourceException("Injected scoring rollback");return scored;}).when(transactions).score(eq(id),eq(1),anyLong());
        accepted(ws.get(0).response(command("ANSWER",id,1,Map.of("text","bàn"))));accepted(ws.get(1).response(command("ANSWER",id,1,Map.of("text","bàn"))));accepted(ws.get(2).response(command("ANSWER",id,1,Map.of("text","bàn"))));
        waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.delay()==100));
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(0);
        assertThat(observer.seen.stream().filter(e->e.type().equals("QUESTION_RESULT") && e.snapshot().gameSessionId()==id)).isEmpty();
        var cancel=new GameCommand(1,"COMMAND",UUID.randomUUID().toString(),"CANCEL_GAME",vn.edu.multigame.realtime.message.common.GameTarget.of(id),null,json.createObjectNode());
        var pending=runtime.command(f.host().id(),cancel,()->{});scheduler.retry(100);pending.get(5,TimeUnit.SECONDS);observer.next("GAME_END",id,1);
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(10);
        assertThat(jdbc.queryForObject("select end_reason from game_session where id=?",String.class,id)).isEqualTo("CANCELLED");assertThat(attempts).hasValue(2);
    }
    @Test void exhaustedRollbackTerminatesV2WithoutCachedAcceptOrPartialScore() throws Exception {
        var f=fixture2(0,1);var ws=online(f);long id=start2(f);ready(ws,id,1);question2(id,1,false);
        doAnswer(inv->{inv.callRealMethod();throw new TransientDataAccessResourceException("Injected persistent Answer rollback");})
            .when(transactions).acceptTyped(eq(id),eq(f.roster().get(0).id()),eq(1),isNull(),any(),anyLong(),anyLong());
        var answer=command("ANSWER",id,1,Map.of("text","bàn"));ws.get(0).send(answer);scheduler.retry(100);scheduler.retry(300);
        rejected(ws.get(0).response(answer.path("requestId").asText(),1),"SERVICE_UNAVAILABLE");observer.next("GAME_END",id,1);
        assertThat(answerCount(id)).isZero();assertThat(runtime.snapshot(id,f.host().id()).endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED);
        assertThat(runtime.snapshot(id,f.host().id()).hasOfficialWinner()).isFalse();assertThat(runtime.snapshot(id,f.host().id()).cleanupPending()).isFalse();
        rejected(ws.get(0).response(answer),"SERVICE_UNAVAILABLE");
    }
    @Test void publicWsAndRestConcurrentStartReplayContinueAndAnswerAfterFinished() throws Exception {
        var f=fixture2(0,1);var ws=online(f);var hostWire=new Wire(f.host());hostWire.subscribe(f);
        var start=envelope("START_GAME","ROOM",f.room().id(),null,Map.of("revision",f.room().revision(),"questionCount",1));
        String request=start.path("requestId").asText();var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        JsonNode original;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var rest=pool.submit(()->{ready.countDown();go.await();return hostWire.client.call("POST","/api/rooms/"+f.room().id()+"/start",new StartGameRequest(request,f.room().revision(),1));});
            var socket=pool.submit(()->{ready.countDown();go.await();return hostWire.response(start);});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();var http=rest.get(10,TimeUnit.SECONDS);original=socket.get(10,TimeUnit.SECONDS);
            assertThat(http.statusCode()).as(http.body()).isEqualTo(200);accepted(original);
            assertThat(json.readTree(http.body()).path("gameSessionId")).isEqualTo(original.path("payload").path("gameSessionId"));
        }
        long id=original.path("payload").path("gameSessionId").asLong();gameIds.add(id);installed.add(id);observer.next("INTRO_STARTED",id,1);
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,f.room().id())).isEqualTo(1);
        var continueCmd=command("CONTINUE",id,1,Map.of());var continueAck=ws.get(0).response(continueCmd);accepted(continueAck);
        ready(ws.subList(1,3),id,1);question2(id,1,false);assertThat(ws.get(0).response(continueCmd)).isEqualTo(continueAck);
        rejected(ws.get(0).response(command("ANSWER",id,1,Map.of("option","A"))),"INVALID_ANSWER_KIND");
        rejected(ws.get(0).response(command("USE_SPIN",id,1,Map.of())),"INVALID_STATE");
        var answer=command("ANSWER",id,1,Map.of("text","bàn"));var answerAck=ws.get(0).response(answer);accepted(answerAck);
        accepted(ws.get(1).response(command("ANSWER",id,1,Map.of("text","bàn"))));accepted(ws.get(2).response(command("ANSWER",id,1,Map.of("text","bàn"))));observer.next("GAME_END",id,1);
        assertThat(hostWire.response(start)).isEqualTo(original);assertThat(ws.get(0).response(continueCmd)).isEqualTo(continueAck);assertThat(ws.get(0).response(answer)).isEqualTo(answerAck);
        assertThat(jdbc.queryForList("select remaining_spins from player_session where game_session_id=?",Integer.class,id)).containsOnly(0);
        assertThat(jdbc.queryForList("select star_available from player_session where game_session_id=?",Boolean.class,id)).containsOnly(false);
    }
    @Test void cancelUnscoredTextHistoryHidesAliasesAndStartupCleanupV2NoWinner() throws Exception {
        var f=fixture2(0,2);var ws=online(f);var host=new Client(f.host());long id=start2(f);ready(ws,id,1);question2(id,1,false);accepted(ws.get(0).response(command("ANSWER",id,1,Map.of("text","bàn"))));
        var cancelled=host.call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString()));assertThat(cancelled.statusCode()).isEqualTo(200);observer.next("GAME_END",id,1);
        var history=json.readTree(host.call("GET","/api/games/history/"+id,null).body());assertThat(history.path("finalSnapshot").path("hasOfficialWinner").asBoolean()).isFalse();assertThat(history.toString()).doesNotContain("acceptedAnswers");
        var answer=history.path("questions").get(0).path("answers").get(0);assertThat(answer.path("answerStatus").asText()).isEqualTo("ACCEPTED_UNSCORED");assertThat(answer.path("result").isNull()).isTrue();assertThat(answer.path("scoredAtMs").isNull()).isTrue();
        // Durable abandoned game on the same Room, with no installed actor: startup cleanup's real transaction path.
        var room=rooms.get(f.host().id(),f.room().id());var saved=roomOps.execute(f.host().auth(),room.id(),UUID.randomUUID().toString(),"START_GAME",Map.of("revision",room.revision(),"questionCount",2),null,()->{},()->transactions.startMultimodeSnapshot(f.host().id(),room.id(),room.revision(),clock.sample().epochMs()),(a,b)->{});
        long stale=saved.gameSessionId();gameIds.add(stale);assertThat(transactions.cleanupAbandoned(clock.sample().epochMs())).isEqualTo(1);
        var finalState=transactions.read(stale,f.host().id()).publicView();assertThat(finalState.endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED);assertThat(finalState.hasOfficialWinner()).isFalse();assertThat(finalState.winners()).isEmpty();
    }
}
