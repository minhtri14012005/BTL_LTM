package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.*;
import vn.edu.multigame.game.enums.GameMode;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** MySQL transactions and real REST/raw WS; controlled clock/latches make timer races reproducible. */
class CluesLifecycleIT extends GameNetworkFixture {
    @org.springframework.beans.factory.annotation.Autowired vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry sockets;
    record Setup(Fixture fixture,long set) {}
    QuestionRequest clue(String content,String answer,long... offsets) {
        return new QuestionRequest(content,null,null,null,List.of(answer),null,null,null,null,
                Arrays.stream(offsets).mapToObj(n -> new HintRequest(n,"Hint "+n)).toList());
    }
    Setup setup(boolean mixed) {
        var host=account();var roster=List.of(account(),account(),account());
        var set=quizService.create(host.auth(),new CreateQuestionBankRequest("Clues",Visibility.PRIVATE,
                List.of(clue("Câu một","bắt cá",0,100,900),clue("Câu hai","bắt cá",0,100,900)),GameMode.CLUES));quizIds.add(set.id());
        var plan=new ArrayList<RoomStageRequest>();plan.add(new RoomStageRequest(GameMode.CLUES,set.id(),2,1000L));
        if(mixed) {var r=quizService.create(host.auth(),new CreateQuestionBankRequest("Riddle",Visibility.PRIVATE,List.of(new QuestionRequest("Đố?",null,null,null,List.of("bắt cá"))),GameMode.RIDDLE));quizIds.add(r.id());plan.add(new RoomStageRequest(GameMode.RIDDLE,r.id(),1,1000L));}
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"Clues Room",3,null,Participation.SPECTATOR,plan))).snapshot();roomIds.add(r.id());long rev=r.revision();
        roomOps.execute(host.auth(),r.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",rev),null,()->{},()->rooms.open(host.id(),r.id(),rev),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),r.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),()->{},()->rooms.join(p.id(),r.id(),r.roomCode(),Participation.PLAYER),(a,b)->{});
        return new Setup(new Fixture(host,roster,0,rooms.get(host.id(),r.id())),set.id());
    }
    List<Wire> online(Fixture f) throws Exception {var ws=new ArrayList<Wire>();for(var p:f.roster()){var w=new Wire(p);w.subscribe(f);ws.add(w);}return ws;}
    long start2(Fixture f) throws Exception {
        var response=new Client(f.host()).call("POST","/api/rooms/"+f.room().id()+"/start",new StartGameRequest(UUID.randomUUID().toString(),f.room().revision(),f.room().stages().stream().mapToInt(s->s.questionCount()).sum()));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);long id=json.readTree(response.body()).path("gameSessionId").asLong();gameIds.add(id);installed.add(id);observer.next("INTRO_STARTED",id,1);return id;
    }
    void ready(List<Wire> ws,long id,int index) throws Exception {for(var w:ws)accepted(w.response(command("CONTINUE",id,index,Map.of())));}
    void advanceDeadline(GameSnapshot s) {long due=Math.max(clock.mono.get(),s.deadlineEpochMs()-clock.epoch);waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get()&&t.due()==due));scheduler.advance(due);}
    int hintCount(long id,int index) {return jdbc.queryForObject("select released_hint_count from game_question where game_session_id=? and order_index=?",Integer.class,id,index);}
    long hintEvents(long id) {return observer.seen.stream().filter(e->e.type().equals("CLUES_RELEASED")&&e.snapshot().gameSessionId()==id).count();}
    Scheduler.Task pendingHint(long due) {waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get()&&t.due()==due));return scheduler.tasks.stream().filter(t->!t.cancelled().get()&&t.due()==due).findFirst().orElseThrow();}

    @Test void immutableTimelineReleasedOnlyReconnectEarlyCloseStaleTimersAndStageTransition() throws Exception {
        var setup=setup(true);var f=setup.fixture();var ws=online(f);long id=start2(f);ready(ws,id,1);var q1=observer.next("QUESTION_START",id,1);long opened=clock.mono.get();
        assertThat(q1.question().payload()).containsOnlyKeys("hints");assertThat((List<?>)q1.question().payload().get("hints")).hasSize(1);
        assertThat(q1.player()).isNull();assertThat(runtime.snapshot(id,f.roster().getFirst().id()).player().remainingSpins()).isZero();
        rejected(ws.get(0).response(command("USE_STAR",id,1,Map.of())),"INVALID_STATE");
        var before=ws.get(0).response(envelope("RECONNECT","GAME",id,null,Map.of()));assertThat(before.toString()).doesNotContain("Hint 100","Hint 900","acceptedAnswers");
        var old=pendingHint(opened+100);scheduler.advance(opened+99);assertThat(hintEvents(id)).isZero();scheduler.advance(opened+100);
        observer.next("CLUES_RELEASED",id,1);assertThat(hintCount(id,1)).isEqualTo(2);var event=ws.get(0).event("CLUES_RELEASED",1);assertThat(event.toString()).doesNotContain("Hint 900","acceptedAnswers");
        var fresh=ws.get(0).response(envelope("RECONNECT","GAME",id,null,Map.of()));assertThat(fresh.path("payload").path("question").path("payload").path("hints").size()).isEqualTo(2);assertThat(fresh.path("payload").path("deadlineEpochMs").asLong()).isEqualTo(q1.deadlineEpochMs());
        var source=quizService.edit(f.host().auth(),setup.set(),new EditQuestionBankRequest("Changed",Visibility.PRIVATE,0L,List.of(clue("Edited secret","khác",0,200)),GameMode.CLUES));assertThat(source.questions().getFirst().hints()).hasSize(2);
        var saved=command("ANSWER",id,1,Map.of("text"," BẮT  CÁ "));var ack=ws.get(0).response(saved);accepted(ack);assertThat(ack.toString()).doesNotContain("CORRECT","acceptedAnswers");
        for(int i=1;i<3;i++)accepted(ws.get(i).response(command("ANSWER",id,1,Map.of("text","bắt cá"))));var result=observer.next("QUESTION_RESULT",id,1);
        assertThat(result.results().stream().map(GameSnapshot.Result::scoreDelta)).containsExactly(10,10,10);assertThat(json.writeValueAsString(result)).contains("bắt cá").doesNotContain("Hint 900","Edited secret");
        old.action().run();assertThat(hintEvents(id)).isEqualTo(1);advanceDeadline(result);var q2=observer.next("QUESTION_START",id,2);long second=clock.mono.get();assertThat(hintCount(id,2)).isEqualTo(1);
        old.action().run();assertThat(hintCount(id,2)).isEqualTo(1);var h2=pendingHint(second+100);scheduler.advance(second+100);waitUntil(()->hintCount(id,2)==2);pendingHint(second+900);scheduler.advance(second+900);waitUntil(()->hintCount(id,2)==3);pendingHint(second+1000);
        clock.mono.set(second+999);for(int i=0;i<2;i++)accepted(ws.get(i).response(command("ANSWER",id,2,Map.of("text","bắt cá"))));
        clock.mono.set(second+1000);rejected(ws.get(2).response(command("ANSWER",id,2,Map.of("text","bắt cá"))),"QUESTION_CLOSED");advanceDeadline(q2);var r2=observer.next("QUESTION_RESULT",id,2);
        assertThat(r2.results().stream().map(GameSnapshot.Result::scoreDelta)).containsExactly(20,20,0);advanceDeadline(r2);observer.next("INTRO_STARTED",id,3);h2.action().run();ready(ws,id,3);observer.next("QUESTION_START",id,3);
        clock.mono.addAndGet(10);for(var w:ws)accepted(w.response(command("ANSWER",id,3,Map.of("text","bắt cá"))));observer.next("GAME_END",id,3);
        assertThat(ws.get(0).response(saved)).isEqualTo(ack);assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(50,50,30);
        var history=json.readTree(ws.get(0).client.call("GET","/api/games/history/"+id,null).body());assertThat(history.path("questions").get(0).path("payload").path("hints").size()).isEqualTo(2);assertThat(history.toString()).doesNotContain("Edited secret");assertThat(observer.committed).containsOnly(true);
    }

    @Test void delayedHintRollbackCannotCloseAheadOfAlreadyIngressedAnswerOrPublishAfterDeadline() throws Exception {
        var s=setup(false);var f=s.fixture();var ws=online(f);long id=start2(f);ready(ws,id,1);var opened=observer.next("QUESTION_START",id,1);long at=clock.mono.get();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var attempts=new AtomicInteger();
        doAnswer(inv->{var committed=inv.callRealMethod();if(attempts.incrementAndGet()==1){entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();throw new TransientDataAccessResourceException("Hint rollback");}return committed;}).when(transactions).releaseHints(eq(id),eq(1),eq(2),any());
        pendingHint(at+100);scheduler.advance(at+100);
        try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();clock.mono.set(at+101);
            // Direct trusted adapter admission returns only after enqueue, so its timestamp is deterministic.
            var cmd=command("ANSWER",id,1,Map.of("text","bắt cá"));var answer=runtime.command(f.roster().getFirst().id(),json.treeToValue(cmd,vn.edu.multigame.realtime.message.command.GameCommand.class),()->{});
            clock.mono.set(at+1000);release.countDown();waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get()&&t.delay()==100));
            assertThat(hintCount(id,1)).isEqualTo(1);assertThat(hintEvents(id)).isZero();scheduler.retry(100);
            assertThat(answer.get(5,TimeUnit.SECONDS).status()).isEqualTo("ACCEPTED");advanceDeadline(opened);var result=observer.next("QUESTION_RESULT",id,1);
            assertThat(result.results().stream().map(GameSnapshot.Result::scoreDelta)).containsExactly(10,0,0);assertThat(hintCount(id,1)).isEqualTo(1);assertThat(hintEvents(id)).isZero();
            assertThat(jdbc.queryForObject("select answer_time_ms from answer where game_session_id=? and answer_status='CORRECT'",Long.class,id)).isEqualTo(101);assertThat(attempts).hasValue(2);
        } finally {release.countDown();}
    }

    @Test void hintCommitRetriesOnceAndCancelPreservesAcceptedUnscoredAndOnlyReleasedPrefix() throws Exception {
        var s=setup(false);var f=s.fixture();var ws=online(f);long id=start2(f);ready(ws,id,1);observer.next("QUESTION_START",id,1);long at=clock.mono.get();var attempts=new AtomicInteger();
        doAnswer(inv->{var result=inv.callRealMethod();if(attempts.incrementAndGet()==1)throw new TransientDataAccessResourceException("Hint rollback");return result;}).when(transactions).releaseHints(eq(id),eq(1),eq(2),any());
        pendingHint(at+100);scheduler.advance(at+100);waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get()&&t.delay()==100));assertThat(hintEvents(id)).isZero();assertThat(hintCount(id,1)).isEqualTo(1);scheduler.retry(100);observer.next("CLUES_RELEASED",id,1);
        assertThat(hintEvents(id)).isEqualTo(1);assertThat(hintCount(id,1)).isEqualTo(2);assertThat(attempts).hasValue(2);
        var late=pendingHint(at+900);var cmd=command("ANSWER",id,1,Map.of("text","bắt cá"));var ack=ws.get(0).response(cmd);accepted(ack);
        assertThat(new Client(f.host()).call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(200);observer.next("GAME_END",id,1);clock.mono.set(at+900);late.action().run();assertThat(hintEvents(id)).isEqualTo(1);
        assertThat(ws.get(0).response(cmd)).isEqualTo(ack);var finalState=ws.get(0).response(envelope("RECONNECT","GAME",id,null,Map.of()));assertThat(finalState.toString()).doesNotContain("Hint 900","acceptedAnswers");
        var detail=json.readTree(ws.get(0).client.call("GET","/api/games/history/"+id,null).body());assertThat(detail.path("questions").get(0).path("answers").get(0).path("answerStatus").asText()).isEqualTo("ACCEPTED_UNSCORED");assertThat(detail.toString()).doesNotContain("acceptedAnswers","Hint 900");
    }

    @Test void finiteHintWriteFailureTerminatesAndDoesNotLeavePartialCursorOrOfficialWinner() throws Exception {
        var s=setup(false);var f=s.fixture();var ws=online(f);long id=start2(f);ready(ws,id,1);observer.next("QUESTION_START",id,1);long at=clock.mono.get();var attempts=new AtomicInteger();
        doAnswer(inv->{inv.callRealMethod();attempts.incrementAndGet();throw new TransientDataAccessResourceException("Hint unavailable");}).when(transactions).releaseHints(eq(id),eq(1),eq(2),any());
        pendingHint(at+100);scheduler.advance(at+100);scheduler.retry(100);scheduler.retry(300);var end=observer.next("GAME_END",id,1);
        assertThat(attempts).hasValue(3);assertThat(end.endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED);assertThat(end.hasOfficialWinner()).isFalse();assertThat(hintCount(id,1)).isEqualTo(1);assertThat(hintEvents(id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();assertThat(jdbc.queryForObject("select status from room where id=?",String.class,f.room().id())).isEqualTo("WAITING");
    }

    @Test void delayedHintsAndReconnectStartNewOfflineEpisodeThatWaitsFirstQuestion() throws Exception {
        var s=setup(false);var f=s.fixture();
        quizService.edit(f.host().auth(),s.set(),new EditQuestionBankRequest("Delayed first hint",Visibility.PRIVATE,0L,List.of(clue("One","bắt cá",100,900),clue("Two","bắt cá",100,900)),GameMode.CLUES));
        var ws=online(f);long id=start2(f);ready(ws,id,1);var q=observer.next("QUESTION_START",id,1);long at=clock.mono.get();
        assertThat((List<?>)q.question().payload().get("hints")).isEmpty();ws.get(0).close();waitUntil(()->!sockets.onlineUsers().contains(f.roster().getFirst().id()));
        pendingHint(at+100);scheduler.advance(at+100);observer.next("CLUES_RELEASED",id,1);
        for(int i=1;i<3;i++)accepted(ws.get(i).response(command("ANSWER",id,1,Map.of("text","bắt cá"))));
        assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);clock.mono.set(at+999);
        var reconnected=new Wire(f.roster().getFirst());var re=reconnected.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(re);
        assertThat(re.path("payload").path("remainingMs").asLong()).isEqualTo(1);assertThat(re.path("payload").path("question").path("payload").path("hints").size()).isEqualTo(1);
        accepted(reconnected.response(command("ANSWER",id,1,Map.of("text","bắt cá"))));var result=observer.next("QUESTION_RESULT",id,1);
        reconnected.close();waitUntil(()->!sockets.onlineUsers().contains(f.roster().getFirst().id()));advanceDeadline(result);var q2=observer.next("QUESTION_START",id,2);long second=clock.mono.get();var hint=pendingHint(second+100);
        for(int i=1;i<3;i++)accepted(ws.get(i).response(command("ANSWER",id,2,Map.of("text","bắt cá"))));
        assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        scheduler.advance(second+100);waitUntil(()->hintCount(id,2)==1);pendingHint(second+900);scheduler.advance(second+900);waitUntil(()->hintCount(id,2)==2);
        advanceDeadline(q2);var end=observer.next("GAME_END",id,2);
        assertThat(end.results().stream().map(GameSnapshot.Result::scoreDelta)).containsExactly(0,20,20);assertThat((List<?>)end.question().payload().get("hints")).hasSize(2);
        hint.action().run();assertThat(hintCount(id,2)).isEqualTo(2);assertThat(hintEvents(id)).isEqualTo(3);
    }

    @Test void authoringPermissionsValidationDurationRevalidationAndMysqlCursorConstraints() throws Exception {
        var s=setup(false);var f=s.fixture();var owner=new Client(f.host());var player=new Client(f.roster().getFirst());
        assertThat(player.call("GET","/api/quizzes/"+s.set(),null).statusCode()).isEqualTo(404);
        var publicSet=quizService.create(f.host().auth(),new CreateQuestionBankRequest("Public clues",Visibility.PUBLIC,List.of(clue("?","đáp án",0,500)),GameMode.CLUES));quizIds.add(publicSet.id());
        var metadata=player.call("GET","/api/quizzes/"+publicSet.id(),null);assertThat(metadata.statusCode()).isEqualTo(200);assertThat(metadata.body()).doesNotContain("questions","hints","acceptedAnswers");
        for(var offsets:List.of(new long[]{},new long[]{-1},new long[]{100,100},new long[]{500,0})) {
            var response=owner.call("POST","/api/quizzes",new CreateQuestionBankRequest("Invalid",Visibility.PUBLIC,List.of(clue("?","answer",offsets)),GameMode.CLUES));assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        }
        for(Object offset:List.of(1.5,"100",new java.math.BigInteger("9223372036854775808"))) {
            var response=owner.call("POST","/api/quizzes",Map.of("title","Invalid offset","visibility","PUBLIC","mode","CLUES","questions",List.of(Map.of("content","?","acceptedAnswers",List.of("answer"),"hints",List.of(Map.of("offsetMs",offset,"text","Hint"))))));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        }
        var bad=owner.call("POST","/api/rooms",new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"Invalid",3,null,Participation.SPECTATOR,List.of(new RoomStageRequest(GameMode.CLUES,publicSet.id(),1,500L)))));assertThat(bad.statusCode()).isEqualTo(409);assertThat(bad.body()).contains("INVALID_HINT_TIMELINE");
        // Config was valid; editing the selected source later must be revalidated atomically by Start.
        quizService.edit(f.host().auth(),s.set(),new EditQuestionBankRequest("Changed",Visibility.PRIVATE,0L,List.of(clue("?","answer",0,1000),clue("?","answer",0,1000)),GameMode.CLUES));
        var rejected=owner.call("POST","/api/rooms/"+f.room().id()+"/start",new StartGameRequest(UUID.randomUUID().toString(),f.room().revision(),2));assertThat(rejected.statusCode()).isEqualTo(409);assertThat(rejected.body()).contains("INVALID_HINT_TIMELINE");assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,f.room().id())).isZero();
        quizService.edit(f.host().auth(),s.set(),new EditQuestionBankRequest("Restored",Visibility.PRIVATE,1L,List.of(clue("?","answer",0,100),clue("?","answer",0,100)),GameMode.CLUES));
        var ws=online(f);long id=start2(f);assertThatThrownBy(()->jdbc.update("update game_question set released_hint_count=1 where game_session_id=? and order_index=1",id)).isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class).hasMessageContaining("3819");
        ready(ws,id,1);observer.next("QUESTION_START",id,1);
        for(int count:List.of(-1,3))assertThatThrownBy(()->jdbc.update("update game_question set released_hint_count=? where game_session_id=? and order_index=1",count,id)).isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class).hasMessageContaining("3819");
    }

    @Test void freshServerStartupInterruptsCluesAndPreservesCommittedReleaseAndUnscoredAnswer() throws Exception {
        var s=setup(false);var f=s.fixture();var time=clock.sample();long id=transactions.start(f.host().id(),f.room().id(),f.room().revision(),2,time.epochMs()).game().publicView().gameSessionId();gameIds.add(id);
        transactions.open(id,1,Phase.INTRO,d->PhaseWindow.open(1,Phase.INTRO,1,time.monotonicMs(),time.epochMs(),d));
        transactions.open(id,1,Phase.QUESTION_OPEN,d->PhaseWindow.open(1,Phase.QUESTION_OPEN,2,time.monotonicMs(),time.epochMs(),d));
        transactions.releaseHints(id,1,2,()->100L);transactions.acceptTyped(id,f.roster().getFirst().id(),1,null,new vn.edu.multigame.game.dto.TypedAnswer("bắt cá",null),time.epochMs()+101,101);
        try(var context=new org.springframework.boot.builder.SpringApplicationBuilder(vn.edu.multigame.MultigameApplication.class).run("--server.port=0","--spring.profiles.active=mysql","--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000","--spring.flyway.enabled=false","--debug=false")) {
            assertThat(context.getBean(GameStartupCleanup.class).ready()).isTrue();var snapshot=context.getBean(vn.edu.multigame.realtime.session.GameRuntime.class).snapshot(id,f.host().id());
            assertThat(snapshot.endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED);assertThat(snapshot.hasOfficialWinner()).isFalse();assertThat((List<?>)snapshot.question().payload().get("hints")).hasSize(2);assertThat(snapshot.question().payload()).doesNotContainKey("acceptedAnswers");
            assertThat(hintCount(id,1)).isEqualTo(2);assertThat(jdbc.queryForObject("select answer_status from answer where game_session_id=?",String.class,id)).isEqualTo("ACCEPTED_UNSCORED");assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
        }
    }
}
