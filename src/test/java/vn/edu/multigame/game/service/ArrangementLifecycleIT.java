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
class ArrangementLifecycleIT extends GameNetworkFixture {
    Fixture arranged(boolean mixed,GameMode single,int count) {
        var host=account();var author=account();var roster=List.of(account(),account(),account());var plan=new ArrayList<RoomStageRequest>();long quiz=0;
        if(mixed) {quiz=quiz(author,1);plan.add(new RoomStageRequest(GameMode.QUIZ,quiz,1,1000L));}
        for(var mode:mixed?List.of(GameMode.VIETNAMESE_PUZZLE,GameMode.ORDERING):List.of(single)) {
            var pieces=List.of(new ArrangementItemRequest("a","ha"),new ArrangementItemRequest("space"," "),new ArrangementItemRequest("b","ha"));
            var items=List.of(new ArrangementItemRequest("a","Một"),new ArrangementItemRequest("b","Một"),new ArrangementItemRequest("c","Ba"));
            var request=new QuestionRequest("Sắp xếp "+mode,null,null,null,mode==GameMode.VIETNAMESE_PUZZLE?List.of("ha ha"):null,
                mode==GameMode.VIETNAMESE_PUZZLE?pieces:null,mode==GameMode.ORDERING?items:null,mode==GameMode.VIETNAMESE_PUZZLE?List.of("a","space","b"):List.of("a","b","c"));
            var set=quizService.create(host.auth(),new CreateQuestionBankRequest("Arrangement "+mode,Visibility.PRIVATE,Collections.nCopies(count,request),mode));quizIds.add(set.id());
            plan.add(new RoomStageRequest(mode,set.id(),count,1000L));
        }
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"Arrangement",10,null,Participation.SPECTATOR,plan))).snapshot();roomIds.add(r.id());long rev=r.revision();
        roomOps.execute(host.auth(),r.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",rev),null,()->{},()->rooms.open(host.id(),r.id(),rev),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),r.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),()->{},()->rooms.join(p.id(),r.id(),r.roomCode(),Participation.PLAYER),(a,b)->{});
        return new Fixture(host,roster,quiz,rooms.get(host.id(),r.id()));
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
    @Test void mixedQuizVietnameseOrderingScoresSnapshotsReconnectHistoryAndTerminalReplay() throws Exception {
        var f=arranged(true,null,2);var ws=online(f);long id=start2(f);ready(ws,id,1);question2(id,1,true);clock.mono.addAndGet(100);
        for(var w:ws)accepted(w.response(command("ANSWER",id,1,Map.of("option","D"))));nextAfterResult(id,1);
        observer.next("INTRO_STARTED",id,2);ready(ws,id,2);JsonNode saved=null,ack=null;
        for(int i=2;i<=5;i++) {
            if(i==4){observer.next("INTRO_STARTED",id,i);ready(ws,id,i);}
            var opened=question2(id,i,false);boolean vua=i<=3;
            var publicPayload=json.valueToTree(opened.question().payload());assertThat(publicPayload.toString()).doesNotContain("correctOrder","acceptedAnswers","matchingPolicy");
            assertThat(publicPayload.path(vua?"pieces":"items")).hasSize(3);
            var order=new ArrayList<String>();publicPayload.path(vua?"pieces":"items").forEach(v->order.add(v.path("id").asText()));
            assertThat(order).isNotEqualTo(vua?List.of("a","space","b"):List.of("a","b","c"));
            var restored=ws.get(0).response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(restored);
            assertThat(restored.path("payload").path("question").path("payload")).isEqualTo(publicPayload);
            if(i==2) {
                var stage=f.room().stages().get(1);var changed=new QuestionRequest("Changed source",null,null,null,List.of("ab"),List.of(new ArrangementItemRequest("a","a"),new ArrangementItemRequest("b","b")),null,List.of("a","b"));
                quizService.edit(f.host().auth(),stage.quizId(),new EditQuestionBankRequest("Changed",Visibility.PRIVATE,0L,List.of(changed),GameMode.VIETNAMESE_PUZZLE));
                assertThat(runtime.snapshot(id,f.host().id()).question().payload()).isEqualTo(opened.question().payload());
                rejected(ws.get(0).response(command("ANSWER",id,i,Map.of("itemIds",List.of("a","fake","b")))),"INVALID_ARRANGEMENT");
                rejected(ws.get(0).response(command("ANSWER",id,i,Map.of("itemIds",List.of("a","b")))),"INVALID_ARRANGEMENT");
                ws.get(0).send(command("ANSWER",id,i,Map.of("itemIds",List.of("a","a","b"))));
                var malformed=ws.get(0).next(m->m.path("kind").asText().equals("ERROR") && m.path("code").asText().equals("INVALID_MESSAGE") && m.path("requestId").isNull());
                rejected(malformed,"INVALID_MESSAGE");
                assertThat(answerCount(id)).isEqualTo(3);
            }
            clock.mono.addAndGet(100);
            for(int p=0;p<3;p++) {
                var ids=vua?List.of("b","space","a"):p==2?List.of("b","a","c"):List.of("a","b","c");
                var cmd=command("ANSWER",id,i,Map.of("itemIds",ids));var receipt=ws.get(p).response(cmd);accepted(receipt);assertThat(receipt.toString()).doesNotContain("correctOrder","acceptedAnswers","CORRECT");
                if(i==2 && p==0){saved=cmd;ack=receipt;assertThat(ws.get(0).response(cmd)).isEqualTo(receipt);rejected(ws.get(0).response(command("ANSWER",id,i,Map.of("itemIds",ids))),"ALREADY_ANSWERED");}
            }
            if(i<5){var result=observer.next("QUESTION_RESULT",id,i);assertThat(result.question().payload()).containsKey("correctOrder");nextAfterResult(id,i);}
        }
        observer.next("GAME_END",id,5);assertThat(ws.get(0).response(saved)).isEqualTo(ack);
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(70,70,40);
        assertThat(jdbc.queryForList("select total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",Long.class,id)).containsExactly(500L,500L,300L);
        assertThat(jdbc.queryForList("select score_delta from answer where game_session_id=? and player_session_id=(select id from player_session where game_session_id=? and user_id=?) order by game_question_id",Integer.class,id,id,f.roster().get(0).id())).containsExactly(10,10,20,10,20);
        var history=json.readTree(ws.get(0).client.call("GET","/api/games/history/"+id,null).body());assertThat(history.path("questions")).hasSize(5);
        assertThat(history.path("questions").get(1).path("payload").path("acceptedAnswers").toString()).contains("ha ha");
        assertThat(history.path("questions").get(1).path("answers").get(0).path("submittedAnswer").path("itemIds")).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select status from room where id=?",String.class,f.room().id())).isEqualTo("WAITING");
    }
    @Test void arrangementDeadlineMinusOneEqualPlusOneAndNoAnswerDoesNotRankTime() throws Exception {
        for(var mode:List.of(GameMode.VIETNAMESE_PUZZLE,GameMode.ORDERING)) {
            var f=arranged(false,mode,1);var ws=online(f);long id=start2(f);ready(ws,id,1);var q=question2(id,1,false);long due=q.deadlineEpochMs()-clock.epoch;
            var ids=mode==GameMode.ORDERING?List.of("a","b","c"):List.of("b","space","a");
            clock.mono.set(due-1);accepted(ws.get(0).response(command("ANSWER",id,1,Map.of("itemIds",ids))));
            clock.mono.set(due);rejected(ws.get(1).response(command("ANSWER",id,1,Map.of("itemIds",ids))),"QUESTION_CLOSED");clock.mono.incrementAndGet();rejected(ws.get(2).response(command("ANSWER",id,1,Map.of("itemIds",ids))),"QUESTION_CLOSED");
            scheduler.advance(clock.mono.get());observer.next("GAME_END",id,1);
            assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(20,0,0);
            assertThat(jdbc.queryForList("select total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",Long.class,id)).containsExactly(999L,0L,0L);
            assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and answer_kind='ARRANGEMENT' and answer_status='NO_ANSWER' and received_at_ms is null",Integer.class,id)).isEqualTo(2);
        }
    }
    @Test void concurrentRetryRollbackAndCancelKeepOneUnscoredArrangementWithoutLeaks() throws Exception {
        var f=arranged(false,GameMode.ORDERING,2);var ws=online(f);long id=start2(f);ready(ws,id,1);question2(id,1,false);
        AtomicInteger attempts=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(inv->{var result=inv.callRealMethod();if(attempts.incrementAndGet()==1){entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Barrier");throw new TransientDataAccessResourceException("Arrangement rollback");}return result;})
            .when(transactions).acceptTyped(eq(id),eq(f.roster().get(0).id()),eq(1),isNull(),any(),anyLong(),anyLong());
        var cmd=command("ANSWER",id,1,Map.of("itemIds",List.of("a","b","c")));ws.get(0).send(cmd);
        try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();ws.get(0).send(cmd);release.countDown();
            waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.delay()==100));assertThat(answerCount(id)).isZero();scheduler.retry(100);
            var ack=ws.get(0).response(cmd.path("requestId").asText(),1);accepted(ack);assertThat(ws.get(0).response(cmd.path("requestId").asText(),2)).isEqualTo(ack);assertThat(attempts).hasValue(2);
            var mismatch=cmd.deepCopy();mismatch.set("payload",json.valueToTree(Map.of("itemIds",List.of("b","a","c"))));rejected(ws.get(0).response(mismatch),"INVALID_REQUEST_ID");
            rejected(ws.get(0).response(command("ANSWER",id,1,Map.of("itemIds",List.of("a","b","c")))),"ALREADY_ANSWERED");
            var replacement=new Wire(f.roster().get(0));assertThat(ws.get(0).closed.get(5,TimeUnit.SECONDS)).isEqualTo(4002);
            var snapshot=replacement.response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(snapshot);assertThat(snapshot.path("payload").path("player").path("submittedAnswer").path("itemIds")).hasSize(3);
            var host=new Client(f.host());assertThat(host.call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(200);observer.next("GAME_END",id,1);
            assertThat(replacement.response(cmd)).isEqualTo(ack);var history=json.readTree(host.call("GET","/api/games/history/"+id,null).body());assertThat(history.toString()).doesNotContain("correctOrder","acceptedAnswers");
            var answer=history.path("questions").get(0).path("answers").get(0);assertThat(answer.path("answerStatus").asText()).isEqualTo("ACCEPTED_UNSCORED");assertThat(answer.path("result").isNull()).isTrue();assertThat(answer.path("scoredAtMs").isNull()).isTrue();assertThat(answerCount(id)).isEqualTo(1);
        } finally {release.countDown();}
    }
}
