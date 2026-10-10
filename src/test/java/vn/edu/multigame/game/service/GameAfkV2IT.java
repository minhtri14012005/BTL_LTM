package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Four Players, 20-second windows, controlled clock and real MySQL/HTTP/raw WS. */
class GameAfkV2IT extends GameNetworkFixture {
    @Autowired AuthenticatedSocketRegistry sockets;
    Fixture afkFixture() {
        var host=account();var roster=List.of(account(),account(),account(),account());
        var item=new QuestionRequest("Question",null,null,null,List.of("answer"));
        var quiz=quizService.create(host.auth(),new CreateQuestionBankRequest("AFK",Visibility.PUBLIC,Collections.nCopies(3,item),GameMode.RIDDLE));
        quizIds.add(quiz.id());
        var config=new RoomConfigRequest(null,"AFK",10,null,Participation.SPECTATOR,List.of(new RoomStageRequest(GameMode.RIDDLE,quiz.id(),3,20000L)));
        var room=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),config)).snapshot();roomIds.add(room.id());
        long revision=room.revision();
        roomOps.execute(host.auth(),room.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",revision),null,()->{},()->rooms.open(host.id(),room.id(),revision),(s,r)->{});
        for(var player:roster)roomOps.execute(player.auth(),room.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",room.roomCode(),"participation","PLAYER"),room.roomCode(),()->{},()->rooms.join(player.id(),room.id(),room.roomCode(),Participation.PLAYER),(s,r)->{});
        return new Fixture(host,roster,quiz.id(),rooms.get(host.id(),room.id()));
    }
    List<Wire> connect(Fixture f,int count) throws Exception {
        var ws=new ArrayList<Wire>();for(int i=0;i<count;i++)ws.add(new Wire(f.roster().get(i)));return ws;
    }
    long startV2(Fixture f) throws Exception {
        var response=new Client(f.host()).call("POST","/api/rooms/"+f.room().id()+"/start",new StartGameRequest(UUID.randomUUID().toString(),f.room().revision(),3));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        long id=json.readTree(response.body()).path("gameSessionId").asLong();gameIds.add(id);installed.add(id);return id;
    }
    void expirePhase(GameSnapshot s) {
        long due=s.deadlineEpochMs()-clock.epoch;
        waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.due()==due));scheduler.advance(Math.max(clock.mono.get(),due));
    }
    GameSnapshot first(long id) {if(observer.seen.stream().noneMatch(e->e.type().equals("QUESTION_START") && e.snapshot().gameSessionId()==id))expirePhase(observer.next("INTRO_STARTED",id,1));return observer.next("QUESTION_START",id,1);}
    GameSnapshot second(long id) {expirePhase(observer.next("QUESTION_RESULT",id,1));return observer.next("QUESTION_START",id,2);}
    void at(GameSnapshot q,long elapsed) {clock.mono.set(q.deadlineEpochMs()-clock.epoch-20000+elapsed);}
    JsonNode answer(Wire w,long id,int index) throws Exception {return w.response(command("ANSWER",id,index,Map.of("text","answer")));}
    JsonNode reconnect(Wire w,long id) throws Exception {var ack=w.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(ack);return ack.path("payload");}
    void abc(List<Wire> ws,long id,GameSnapshot q) throws Exception {at(q,3000);accepted(answer(ws.get(0),id,q.questionIndex()));at(q,5000);accepted(answer(ws.get(1),id,q.questionIndex()));at(q,8000);accepted(answer(ws.get(2),id,q.questionIndex()));reconnect(ws.get(0),id);}
    void disconnect(Wire w) throws Exception {w.close();waitUntil(()->!sockets.onlineUsers().contains(w.client.account.id()));}
    void noAnswer(long id,int index,long user) {
        assertThat(jdbc.queryForObject("select a.answer_status from answer a join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id where a.game_session_id=? and q.order_index=? and p.user_id=?",String.class,id,index,user)).isEqualTo("NO_ANSWER");
    }
    void scoredOnce(long id,int index) {
        assertThat(observer.seen.stream().filter(e->e.snapshot().gameSessionId()==id && e.snapshot().questionIndex()==index)
                .map(GameLifecycleEvent::type).toList()).containsSubsequence("QUESTION_START","QUESTION_CLOSED","SCORING_STARTED","QUESTION_RESULT");
        assertThat(observer.seen.stream().filter(e->e.type().equals("QUESTION_RESULT") && e.snapshot().gameSessionId()==id && e.snapshot().questionIndex()==index)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from answer a join game_question q on q.id=a.game_question_id where a.game_session_id=? and q.order_index=? and a.scored_at_ms is not null",Integer.class,id,index)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from player_session where game_session_id=? and player_state='PLAYING'",Integer.class,id)).isEqualTo(4);
        assertThat(observer.committed).containsOnly(true);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void offlineBeforeFirstQuestionStillWaitsThenAcceptsAtNineSeconds(int disconnectPoint) throws Exception {
        var f=afkFixture();var ws=connect(f,disconnectPoint==0?3:4);
        if(disconnectPoint==1)disconnect(ws.get(3));
        long id=startV2(f);if(disconnectPoint==2)disconnect(ws.get(3));var q=first(id);abc(ws,id,q);
        assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        at(q,9000);var d=new Wire(f.roster().get(3));var restored=reconnect(d,id);
        assertThat(restored.path("deadlineEpochMs").asLong()).isEqualTo(q.deadlineEpochMs());accepted(answer(d,id,1));
        var result=observer.next("QUESTION_RESULT",id,1);assertThat(result.results()).hasSize(4);
        assertThat(result.results().stream().filter(r->r.userId()==f.roster().get(3).id()).findFirst().orElseThrow().outcome()).isEqualTo(AnswerStatus.CORRECT);
        assertThat(observer.committed).containsOnly(true);
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(10);
        scoredOnce(id,1);
    }
    @Test void firstOfflineQuestionTimesOutThenReconnectCanAnswerSecondWithoutJoiningWaitSet() throws Exception {
        var f=afkFixture();var ws=connect(f,3);long id=startV2(f);var q=first(id);abc(ws,id,q);
        assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        at(q,19999);reconnect(ws.get(0),id);assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        expirePhase(q);observer.next("QUESTION_RESULT",id,1);noAnswer(id,1,f.roster().get(3).id());scoredOnce(id,1);
        var q2=second(id);at(q2,3000);accepted(answer(ws.get(0),id,2));
        at(q2,4000);var d=new Wire(f.roster().get(3));var restored=reconnect(d,id);
        assertThat(restored.path("remainingMs").asLong()).isEqualTo(16000);var request=command("ANSWER",id,2,Map.of("text","answer"));
        var ack=d.response(request);accepted(ack);assertThat(d.response(request)).isEqualTo(ack);
        assertThat(reconnect(d,id).path("player").path("alreadyAnswered").asBoolean()).isTrue();
        accepted(answer(ws.get(1),id,2));accepted(answer(ws.get(2),id,2));observer.next("QUESTION_RESULT",id,2);scoredOnce(id,2);
        assertThat(jdbc.queryForObject("select score from player_session where game_session_id=? and user_id=?",Integer.class,id,f.roster().get(3).id())).isEqualTo(10);
    }
    @Test void secondQuestionClosesEarlyAndLateReconnectCannotReopenOrAnswerAndOldTimerIsHarmless() throws Exception {
        var f=afkFixture();var ws=connect(f,3);long id=startV2(f);var q=first(id);abc(ws,id,q);expirePhase(q);observer.next("QUESTION_RESULT",id,1);
        var q2=second(id);long due=q2.deadlineEpochMs()-clock.epoch;
        waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.due()==due));
        var stale=scheduler.tasks.stream().filter(t->!t.cancelled().get() && t.due()==due).findFirst().orElseThrow();abc(ws,id,q2);
        var result=observer.next("QUESTION_RESULT",id,2);assertThat(clock.mono.get()).isLessThan(due);noAnswer(id,2,f.roster().get(3).id());
        var d=new Wire(f.roster().get(3));assertThat(reconnect(d,id).path("phase").asText()).isEqualTo("RESULT");rejected(answer(d,id,2),"QUESTION_CLOSED");
        expirePhase(result);observer.next("QUESTION_START",id,3);stale.action().run();reconnect(d,id);
        assertThat(runtime.snapshot(id,f.host().id()).questionIndex()).isEqualTo(3);assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);scoredOnce(id,2);
    }
    @Test void disconnectAtFourSecondsKeepsFirstQuestionWaitingAtEight() throws Exception {
        var f=afkFixture();var ws=connect(f,4);long id=startV2(f);var q=first(id);
        at(q,3000);accepted(answer(ws.get(0),id,1));at(q,4000);disconnect(ws.get(3));
        at(q,5000);accepted(answer(ws.get(1),id,1));at(q,8000);accepted(answer(ws.get(2),id,1));reconnect(ws.get(0),id);
        assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        expirePhase(q);observer.next("QUESTION_RESULT",id,1);noAnswer(id,1,f.roster().get(3).id());
        var q2=second(id);abc(ws,id,q2);observer.next("QUESTION_RESULT",id,2);scoredOnce(id,2);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void reconnectDuringQuestionOrBetweenQuestionsStartsFreshEpisode(boolean duringQuestion) throws Exception {
        var f=afkFixture();var ws=connect(f,3);long id=startV2(f);var q=first(id);abc(ws,id,q);
        Wire d;
        if(duringQuestion) {at(q,9000);d=new Wire(f.roster().get(3));reconnect(d,id);accepted(answer(d,id,1));}
        else {expirePhase(q);observer.next("QUESTION_RESULT",id,1);d=new Wire(f.roster().get(3));reconnect(d,id);}
        observer.next("QUESTION_RESULT",id,1);disconnect(d);
        var q2=second(id);abc(ws,id,q2);assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);
        expirePhase(q2);observer.next("QUESTION_RESULT",id,2);noAnswer(id,2,f.roster().get(3).id());
        expirePhase(observer.next("QUESTION_RESULT",id,2));var q3=observer.next("QUESTION_START",id,3);abc(ws,id,q3);observer.next("GAME_END",id,3);scoredOnce(id,3);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void excludedPlayersAnswerAlreadyQueuedBeforeCloseIsAcceptedInEitherAnswerOrder(boolean dBeforeClose) throws Exception {
        var f=afkFixture();var ws=connect(f,3);long id=startV2(f);var q=first(id);expirePhase(q);observer.next("QUESTION_RESULT",id,1);
        var q2=second(id);var d=new Wire(f.roster().get(3));reconnect(d,id);at(q2,3000);accepted(answer(ws.get(0),id,2));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(inv->{var committed=inv.callRealMethod();entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Barrier");return committed;})
            .when(transactions).acceptTyped(eq(id),eq(f.roster().get(1).id()),eq(2),isNull(),any(),anyLong(),anyLong());
        try {
            at(q2,5000);var b=runtime.command(f.roster().get(1).id(),json.treeToValue(command("ANSWER",id,2,Map.of("text","answer")),vn.edu.multigame.realtime.message.command.GameCommand.class),()->{});
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();at(q2,8000);
            var dc=json.treeToValue(command("ANSWER",id,2,Map.of("text","answer")),vn.edu.multigame.realtime.message.command.GameCommand.class);
            var cc=json.treeToValue(command("ANSWER",id,2,Map.of("text","answer")),vn.edu.multigame.realtime.message.command.GameCommand.class);
            CompletableFuture<?> df,cf;
            if(dBeforeClose){df=runtime.command(f.roster().get(3).id(),dc,()->{});cf=runtime.command(f.roster().get(2).id(),cc,()->{});}
            else {cf=runtime.command(f.roster().get(2).id(),cc,()->{});df=runtime.command(f.roster().get(3).id(),dc,()->{});}
            release.countDown();b.get(10,TimeUnit.SECONDS);cf.get(10,TimeUnit.SECONDS);
            df.get(10,TimeUnit.SECONDS);
            observer.next("QUESTION_RESULT",id,2);scoredOnce(id,2);
            assertThat(jdbc.queryForObject("select count(*) from answer a join game_question q on q.id=a.game_question_id where a.game_session_id=? and q.order_index=2 and a.answer_status='CORRECT'",Integer.class,id)).isEqualTo(4);
        } finally {release.countDown();}
    }
    @Test void allOfflineEmptyNextWaitSetStillUsesDeadlineAndReconnectAtDeadlineGetsNoExtraTime() throws Exception {
        var f=afkFixture();var ws=connect(f,4);long id=startV2(f);var q=first(id);for(var w:ws)disconnect(w);expirePhase(q);observer.next("QUESTION_RESULT",id,1);var q2=second(id);
        at(q2,19999);runtime.reconnect(id,f.host().id(),()->{},s->assertThat(s.phase()).isEqualTo(Phase.QUESTION_OPEN)).get(5,TimeUnit.SECONDS);
        at(q2,20000);var d=new Wire(f.roster().get(3));assertThat(reconnect(d,id).path("remainingMs").asLong()).isZero();rejected(answer(d,id,2),"QUESTION_CLOSED");
        expirePhase(q2);observer.next("QUESTION_RESULT",id,2);scoredOnce(id,2);noAnswer(id,2,f.roster().get(3).id());
    }
    @Test void answerEnqueuedAfterCloseHasStartedIsRejectedEvenBeforeOriginalDeadline() throws Exception {
        var f=afkFixture();var ws=connect(f,3);long id=startV2(f);var q=first(id);expirePhase(q);observer.next("QUESTION_RESULT",id,1);var q2=second(id);
        var d=new Wire(f.roster().get(3));reconnect(d,id);at(q2,3000);accepted(answer(ws.get(0),id,2));at(q2,5000);accepted(answer(ws.get(1),id,2));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Barrier");return inv.callRealMethod();})
            .when(transactions).transition(eq(id),eq(2),eq(Phase.QUESTION_OPEN),eq(Phase.QUESTION_CLOSED));
        try {
            at(q2,8000);accepted(answer(ws.get(2),id,2));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var dc=json.treeToValue(command("ANSWER",id,2,Map.of("text","answer")),vn.edu.multigame.realtime.message.command.GameCommand.class);
            var late=runtime.command(f.roster().get(3).id(),dc,()->{});release.countDown();
            assertThatThrownBy(()->late.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(GameFailure.class).hasRootCauseMessage("QUESTION_CLOSED");
            observer.next("QUESTION_RESULT",id,2);scoredOnce(id,2);noAnswer(id,2,f.roster().get(3).id());
        } finally {release.countDown();}
    }
    @Test void reconnectThenDisconnectAgainWithinFirstQuestionDoesNotCarryOldEpisodeForward() throws Exception {
        var f=afkFixture();var ws=connect(f,3);long id=startV2(f);var q=first(id);abc(ws,id,q);at(q,9000);
        var d=new Wire(f.roster().get(3));reconnect(d,id);at(q,10000);disconnect(d);reconnect(ws.get(0),id);
        assertThat(runtime.snapshot(id,f.host().id()).phase()).isEqualTo(Phase.QUESTION_OPEN);expirePhase(q);observer.next("QUESTION_RESULT",id,1);
        var q2=second(id);abc(ws,id,q2);observer.next("QUESTION_RESULT",id,2);scoredOnce(id,2);
    }
    @Test void spectatorAndPresenceInAnotherGameCannotHoldOrReleaseThisGame() throws Exception {
        var f=afkFixture();var g=afkFixture();var ws=connect(f,4);var other=connect(g,3);var host=new Wire(f.host());long id=startV2(f);long gid=startV2(g);
        var q=first(id);var gq=first(gid);disconnect(other.get(0));disconnect(host);abc(ws,id,q);at(q,9000);accepted(answer(ws.get(3),id,1));observer.next("QUESTION_RESULT",id,1);
        runtime.reconnect(gid,g.host().id(),()->{},s->assertThat(s.phase()).isEqualTo(Phase.QUESTION_OPEN)).get(5,TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=?",Integer.class,gid)).isZero();scoredOnce(id,1);
    }
}
