package vn.edu.quiz.game.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.quiz.enums.Option;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual cookie/HTTP/raw WS/MySQL; controlled queue/clock and faults after SQL, no substitute DB. */
class GameHistoryCancelIT extends GameNetworkFixture {
    com.fasterxml.jackson.databind.node.ObjectNode cancel(long game) {return envelope("CANCEL_GAME","GAME",game,null,Map.of());}
    String history(long game) {return "/api/games/history/"+game;}
    void released(long game,long room) {
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,game)).isZero();
        assertThat(jdbc.queryForObject("select status from room where id=?",String.class,room)).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("select status from game_session where id=?",String.class,game)).isEqualTo("FINISHED");
    }
    @Test void restCancelPreservesAcceptedUnscoredAndReplaysAcrossWsAfterFinish() throws Exception {
        var f=fixture();var host=new Wire(f.host());var player=new Wire(f.roster().getFirst());host.subscribe(f);player.subscribe(f);
        long id=start(f);doReturn(SpinEffect.BONUS).when(spins).select(any(),any());
        accepted(player.response(command("USE_SPIN",id,1,Map.of())));accepted(player.response(command("USE_STAR",id,1,Map.of())));
        open(id,1);scheduler.advance(clock.mono.get()+20);var answer=command("ANSWER",id,1,Map.of("option","D"));var original=player.response(answer);accepted(original);
        var command=cancel(id);var response=host.client.call("POST","/api/games/"+id+"/cancel",Map.of("requestId",command.path("requestId").asText()));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);var rest=json.readTree(response.body());
        var end=host.event("GAME_END",1);assertThat(end.path("payload").path("endReason").asText()).isEqualTo("CANCELLED");
        assertThat(end.path("payload").path("hasOfficialWinner").asBoolean()).isFalse();assertThat(end.path("payload").path("winners").isEmpty()).isTrue();
        var room=host.next(m -> m.path("type").asText().equals("ROOM_UPDATED") && m.path("payload").path("status").asText().equals("WAITING"));
        assertThat(host.messages.indexOf(room)).isGreaterThan(host.messages.indexOf(end));released(id,f.room().id());
        var replay=host.response(command);accepted(replay);assertThat(replay.path("questionIndex").isNull()).isTrue();assertThat(replay.path("payload").path("finalSnapshot")).isEqualTo(rest.path("finalSnapshot"));
        assertThat(host.client.call("POST","/api/games/"+id+"/cancel",Map.of("requestId",command.path("requestId").asText())).body()).isEqualTo(response.body());
        assertThat(player.response(answer)).isEqualTo(original);
        var detailResponse=host.client.call("GET",history(id),null);assertThat(detailResponse.statusCode()).isEqualTo(200);var detail=json.readTree(detailResponse.body());
        assertThat(detail.path("finalSnapshot").path("player").isNull()).isTrue();assertThat(detail.path("finalSnapshot").path("serverTimeMs").asLong()).isPositive();
        GameSnapshotAssertions.snapshot(detail.path("finalSnapshot"),f.host().id());
        assertThat(detail.path("questions").size()).isEqualTo(1);var q=detail.path("questions").get(0);assertThat(q.path("correctAnswer").isNull()).isTrue();
        assertThat(q.path("answers").size()).isEqualTo(1);var a=q.path("answers").get(0);assertThat(a.path("answerStatus").asText()).isEqualTo("ACCEPTED_UNSCORED");
        assertThat(a.path("selectedOption").asText()).isEqualTo("D");assertThat(a.path("answerTimeMs").asLong()).isEqualTo(20);
        for(String field:List.of("scoredAtMs","baseDelta","scoreDelta","scoreAfter","result"))assertThat(a.path(field).isNull()).as(field).isTrue();
        assertThat(a.path("spinEffect").asText()).isEqualTo("BONUS");assertThat(a.path("starSelected").asBoolean()).isTrue();
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(20);
        assertThat(jdbc.queryForList("select total_answer_time_ms from player_session where game_session_id=?",Long.class,id)).containsOnly(0L);
        assertThat(answerCount(id)).isEqualTo(1);
        var outsider=new Client(account());assertThat(outsider.call("GET",history(id),null).statusCode()).isEqualTo(403);
        assertThat(json.readTree(outsider.call("GET","/api/games/history",null).body()).path("totalElements").asInt()).isZero();
        var member=f.roster().getFirst();
        roomOps.execute(member.auth(),f.room().id(),UUID.randomUUID().toString(),"LEAVE_ROOM",Map.of(),null,() -> {},
                () -> rooms.leave(member.id(),f.room().id()),(s,r) -> {});
        assertThat(new Client(member).call("GET",history(id),null).statusCode()).isEqualTo(200); // Immutable GameMember, not current JOINED.
    }
    @Test void cancelAuthorizationStrictRestAndHistoryPagination() throws Exception {
        var f=fixture();var host=new Wire(f.host());var player=new Wire(f.roster().getFirst());var outsider=new Wire(account());long id=start(f);
        rejected(player.response(cancel(id)),"FORBIDDEN");rejected(outsider.response(cancel(id)),"FORBIDDEN");
        assertThat(host.client.call("GET",history(id),null).statusCode()).isEqualTo(409);
        assertThat(player.client.call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(403);
        for(Object body:List.of(Map.of("requestId","invalid"),Map.of("requestId",UUID.randomUUID().toString(),"userId",f.host().id())))
            assertThat(host.client.call("POST","/api/games/"+id+"/cancel",body).statusCode()).isEqualTo(400);
        String csrf=host.client.csrf;host.client.csrf=null;assertThat(host.client.call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(403);host.client.csrf=csrf;
        var anonymous=java.net.http.HttpClient.newHttpClient();try{assertThat(anonymous.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+history(id))).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);}finally{anonymous.close();}
        accepted(host.response(cancel(id)));var page=json.readTree(host.client.call("GET","/api/games/history?size=1",null).body());assertThat(page.path("items").size()).isEqualTo(1);assertThat(page.path("items").get(0).path("hasOfficialWinner").asBoolean()).isFalse();
        assertThat(json.readTree(host.client.call("GET","/api/games/history?page=1&size=1",null).body()).path("items").isEmpty()).isTrue();
        for(String query:List.of("page=-1","size=0","size=101","size=no","page=2147483647&size=100"))assertThat(host.client.call("GET","/api/games/history?"+query,null).statusCode()).isEqualTo(400);
        assertThat(host.client.call("GET",history(Long.MAX_VALUE),null).statusCode()).isEqualTo(404);
        rejected(host.response(cancel(id)),"INVALID_STATE");
        var next=new Fixture(f.host(),f.roster(),f.quiz(),rooms.get(f.host().id(),f.room().id()));long second=start(next);accepted(host.response(cancel(second)));
        var firstPage=json.readTree(host.client.call("GET","/api/games/history?size=1",null).body());assertThat(firstPage.path("totalElements").asInt()).isEqualTo(2);assertThat(firstPage.path("items").get(0).path("gameSessionId").asLong()).isEqualTo(second);
        assertThat(json.readTree(host.client.call("GET","/api/games/history?page=1&size=1",null).body()).path("items").get(0).path("gameSessionId").asLong()).isEqualTo(id);
    }
    @Test void cancelBeforeTimerCloseDoesNotScoreAndDuplicateConcurrentCancelHasOneCommit() throws Exception {
        var f=fixture();var host=new Wire(f.host());long id=start(f);var opened=open(id,1);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        doAnswer(inv -> {calls.incrementAndGet();entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return inv.callRealMethod();}).when(transactions).cancel(eq(id),eq(f.host().id()),anyLong());
        var command=cancel(id);host.send(command);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        host.send(command);expire(opened);release.countDown();var first=host.response(command.path("requestId").asText(),1);accepted(first);
        assertThat(host.response(command.path("requestId").asText(),2)).isEqualTo(first);assertThat(calls.get()).isEqualTo(1);
        verify(transactions,never()).score(eq(id),anyInt(),anyLong());assertThat(answerCount(id)).isZero();released(id,f.room().id());
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(20);
    }
    @Test void scoringInProgressCommitsAllPlayersThenQueuedCancelKeepsWholeQuestion() throws Exception {
        var f=fixture();var host=new Wire(f.host());long id=start(f);var opened=open(id,1);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(inv -> {entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return null;}).when(players).flush();
        expire(opened);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var command=cancel(id);host.send(command);
        assertThat(runtime.snapshot(id,f.host().id()).members().stream().filter(m -> m.score()!=null).map(m -> m.score())).containsOnly(20);
        release.countDown();accepted(host.response(command.path("requestId").asText(),1));released(id,f.room().id());
        var detail=json.readTree(host.client.call("GET",history(id),null).body());var answers=detail.path("questions").get(0).path("answers");assertThat(answers.size()).isEqualTo(3);
        for(var a:answers){assertThat(a.path("answerStatus").asText()).isEqualTo("NO_ANSWER");assertThat(a.path("selectedOption").isNull()).isTrue();assertThat(a.path("receivedAtMs").isNull()).isTrue();assertThat(a.path("answerTimeMs").asLong()).isEqualTo(1000);assertThat(a.path("scoreAfter").asInt()).isEqualTo(19);assertThat(a.path("scoreDelta").asInt()).isEqualTo(-1);}
        verify(transactions,times(1)).score(eq(id),eq(1),anyLong());
        runtime.reconnect(id,f.host().id(),() -> {},s -> assertThat(s.runtimeState()).isEqualTo("FINISHED")).get(5,TimeUnit.SECONDS);
        assertThat(observer.seen.stream().noneMatch(e -> e.snapshot().gameSessionId()==id && e.type().equals("GAME_UNAVAILABLE"))).isTrue();
    }
    @Test void eliminatedHostCanCancelButCannotAnswerSpinStarAndHistoryKeepsElimination() throws Exception {
        var hostAccount=account();var roster=List.of(hostAccount,account(),account());var author=account();long quiz=quiz(author,10);var f=new Fixture(hostAccount,roster,quiz,room(hostAccount,quiz,roster));
        var host=new Wire(hostAccount);var b=new Wire(roster.get(1));var c=new Wire(roster.get(2));long id=start(f);
        for(int index=1;index<=6;index++){open(id,index);accepted(host.response(command("ANSWER",id,index,Map.of("option","A"))));accepted(b.response(command("ANSWER",id,index,Map.of("option","D"))));accepted(c.response(command("ANSWER",id,index,Map.of("option","D"))));observer.next("DECISION_STARTED",id,index+1);}
        assertThat(runtime.snapshot(id,hostAccount.id()).player().score()).isEqualTo(-1); // 5 wrong:0, Recovery; sixth -1.
        assertThat(runtime.snapshot(id,hostAccount.id()).player().state()).isEqualTo(PlayerState.ELIMINATED);
        for(String type:List.of("ANSWER","USE_SPIN","USE_STAR"))rejected(host.response(command(type,id,7,type.equals("ANSWER")?Map.of("option","D"):Map.of())),"FORBIDDEN");
        accepted(host.response(cancel(id)));var detail=json.readTree(host.client.call("GET",history(id),null).body());
        assertThat(detail.path("finalSnapshot").path("player").path("score").asInt()).isEqualTo(-1);assertThat(detail.path("finalSnapshot").path("player").path("eliminatedQuestionIndex").asInt()).isEqualTo(6);
        assertThat(detail.path("questions").size()).isEqualTo(6);assertThat(detail.path("finalSnapshot").path("hasOfficialWinner").asBoolean()).isFalse();
        assertThat(new Client(author).call("GET",history(id),null).statusCode()).isEqualTo(403);
    }
    @Test void completedHistoryContainsStreakEffectsAndNormalCoWinnersCancelCannotRewriteIt() throws Exception {
        var f=fixture();var host=new Wire(f.host());var ws=f.roster().stream().map(p -> {try{return new Wire(p);}catch(Exception e){throw new RuntimeException(e);}}).toList();long id=start(f);
        for(int index=1;index<=10;index++){open(id,index);for(var wire:ws)accepted(wire.response(command("ANSWER",id,index,Map.of("option","D"))));observer.next(index==10?"GAME_END":"DECISION_STARTED",id,index==10?10:index+1);}
        rejected(host.response(cancel(id)),"INVALID_STATE");var detail=json.readTree(host.client.call("GET",history(id),null).body());
        assertThat(detail.path("finalSnapshot").path("endReason").asText()).isEqualTo("COMPLETED");assertThat(detail.path("finalSnapshot").path("hasOfficialWinner").asBoolean()).isTrue();assertThat(detail.path("finalSnapshot").path("winners").size()).isEqualTo(3);
        for(var member:detail.path("finalSnapshot").path("members"))if(!member.path("score").isNull()){assertThat(member.path("score").asInt()).isEqualTo(123);assertThat(member.path("rank").asInt()).isEqualTo(1);}
        var q5=detail.path("questions").get(4).path("answers").get(0);var q6=detail.path("questions").get(5).path("answers").get(0);
        assertThat(q5.path("result").path("momentumGranted").asBoolean()).isTrue();assertThat(q5.path("scoreDelta").asInt()).isEqualTo(10);
        assertThat(q6.path("result").path("momentumConsumed").asBoolean()).isTrue();assertThat(q6.path("scoreDelta").asInt()).isEqualTo(13);
        assertThat(q6.path("starSelected").asBoolean()).isFalse();assertThat(q6.path("spinEffect").isNull()).isTrue();
        quizService.delete(f.host().auth(),f.quiz(),quizzes.findById(f.quiz()).orElseThrow().getRevision());
        assertThat(json.readTree(host.client.call("GET",history(id),null).body()).path("questions").get(0).path("content").asText()).startsWith("Original question");
    }
    @Test void cancelRollbackRetriesWithoutSuccessBeforeCommit() throws Exception {
        var f=fixture();var host=new Wire(f.host());long id=start(f);AtomicInteger attempts=new AtomicInteger();
        doAnswer(inv -> {if(attempts.incrementAndGet()<=2)throw new TransientDataAccessResourceException("Controlled failure after UAG SQL");return null;}).when(players).flush();
        var command=cancel(id);host.send(command);scheduler.retry(100);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isEqualTo(4);
        assertThat(observer.seen.stream().noneMatch(e -> e.snapshot().gameSessionId()==id && e.type().equals("GAME_END"))).isTrue();
        scheduler.retry(300);accepted(host.response(command.path("requestId").asText(),1));assertThat(attempts.get()).isEqualTo(3);released(id,f.room().id());
        assertThat(host.response(command).path("kind").asText()).isEqualTo("ACK");assertThat(attempts.get()).isEqualTo(3);
    }
    @Test void exhaustedCancelRollbackDurablyInterruptsAndDoesNotCacheAccept() throws Exception {
        var f=fixture();var host=new Wire(f.host());long id=start(f);AtomicInteger attempts=new AtomicInteger();
        doAnswer(inv -> {if(attempts.incrementAndGet()<=3)throw new TransientDataAccessResourceException("Controlled cancellation rollback");return null;}).when(players).flush();
        var command=cancel(id);host.send(command);scheduler.retry(100);scheduler.retry(300);rejected(host.response(command.path("requestId").asText(),1),"SERVICE_UNAVAILABLE");
        observer.next("GAME_END",id,1);released(id,f.room().id());rejected(host.response(command),"SERVICE_UNAVAILABLE");
        var detail=json.readTree(host.client.call("GET",history(id),null).body());assertThat(detail.path("finalSnapshot").path("endReason").asText()).isEqualTo("SERVER_INTERRUPTED");
        assertThat(detail.path("finalSnapshot").path("hasOfficialWinner").asBoolean()).isFalse();assertThat(detail.path("finalSnapshot").path("winners").isEmpty()).isTrue();
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(20);
    }
    @Test void continuedDbWriteFailureKeepsPendingDurableStateAndHistoryNotReady() throws Exception {
        var f=fixture();var host=new Wire(f.host());long id=start(f);AtomicInteger attempts=new AtomicInteger();
        doAnswer(inv -> {attempts.incrementAndGet();throw new TransientDataAccessResourceException("Controlled persistent DB write failure");}).when(players).flush();
        var command=cancel(id);host.send(command);scheduler.retry(100);scheduler.retry(300);rejected(host.response(command.path("requestId").asText(),1),"SERVICE_UNAVAILABLE");
        scheduler.retry(100);scheduler.retry(300);waitUntil(() -> attempts.get()==6);
        var state=runtime.snapshot(id,f.host().id());assertThat(state.runtimeState()).isEqualTo("UNAVAILABLE");assertThat(state.cleanupPending()).isTrue();assertThat(state.status()).isEqualTo(GameStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isEqualTo(4);
        assertThat(host.client.call("GET",history(id),null).statusCode()).isEqualTo(409);
        assertThat(observer.seen.stream().noneMatch(e -> e.snapshot().gameSessionId()==id && e.type().equals("GAME_END"))).isTrue();
        reset(players); // Fixture cleanup represents restoring writes, not runtime crash recovery.
    }
    @Test void realFreshServerStartupCleansAbandonedStateAndHistorySurvivesWithoutRuntime() throws Exception {
        var f=fixture();var time=clock.sample();var started=transactions.start(f.host().id(),f.room().id(),f.room().revision(),10,time.epochMs());long id=started.game().publicView().gameSessionId();gameIds.add(id);
        transactions.open(id,1,Phase.DECISION,d -> vn.edu.quiz.game.runtime.PhaseWindow.open(1,Phase.DECISION,1,time.monotonicMs(),time.epochMs(),d));
        transactions.open(id,1,Phase.QUESTION_OPEN,d -> vn.edu.quiz.game.runtime.PhaseWindow.open(1,Phase.QUESTION_OPEN,2,time.monotonicMs()+5000,time.epochMs()+5000,d));
        transactions.accept(id,f.roster().getFirst().id(),1,Option.D,time.epochMs()+5020,20);
        try(var context=new org.springframework.boot.builder.SpringApplicationBuilder(vn.edu.quiz.QuizApplication.class).run(
                "--server.port=0","--spring.profiles.active=mysql","--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000","--spring.flyway.enabled=false","--debug=false")) {
            assertThat(context.getBean(GameStartupCleanup.class).ready()).isTrue();released(id,f.room().id());
            assertThat(jdbc.queryForObject("select finished_at_ms from game_session where id=?",Long.class,id)).isGreaterThanOrEqualTo(time.epochMs()+5020);
            var state=context.getBean(vn.edu.quiz.realtime.session.GameRuntime.class).snapshot(id,f.host().id());assertThat(state.endReason()).isEqualTo(EndReason.SERVER_INTERRUPTED);assertThat(state.hasOfficialWinner()).isFalse();
            int actualPort=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)context).getWebServer().getPort();
            var cookie=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);var http=java.net.http.HttpClient.newBuilder().cookieHandler(cookie).build();
            try{
                String base="http://127.0.0.1:"+actualPort;var token=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                String csrf=json.readTree(token.body()).path("token").asText();
                assertThat(http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf).POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("username",f.host().name(),"password",PASSWORD)))).build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
                var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+history(id))).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);
                assertThat(json.readTree(response.body()).path("questions").get(0).path("answers").get(0).path("answerStatus").asText()).isEqualTo("ACCEPTED_UNSCORED");
            }finally{http.close();}
        }
    }
    @Test void actualMysqlConnectionLossRollsBackCancelAndCommitsInterruptedCleanup() throws Exception {
        var f=fixture();var host=new Wire(f.host());var player=new Wire(f.roster().getFirst());long id=start(f);open(id,1);
        accepted(player.response(command("ANSWER",id,1,Map.of("option","D"))));var killed=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(inv -> {
            if(killed.compareAndSet(false,true)) {
                assertThat(jdbc.queryForObject("select database()",String.class)).isEqualTo("quizz_task2_test");
                assertThat(jdbc.queryForObject("select @@autocommit",Integer.class)).isZero();
                long victim=jdbc.queryForObject("select connection_id()",Long.class);
                // Kill only this test's bound transaction connection, from a different pool connection.
                try(var killer=jdbc.getDataSource().getConnection();var statement=killer.createStatement()) {
                    try(var row=statement.executeQuery("select connection_id()")){row.next();assertThat(row.getLong(1)).isNotEqualTo(victim);}
                    statement.execute("KILL CONNECTION "+victim);
                }
                jdbc.queryForObject("select 1",Integer.class); // Real socket/SQL error, no injected replacement exception.
                throw new AssertionError("Killed MySQL session unexpectedly survived");
            }
            return null;
        }).when(players).flush();
        rejected(host.response(cancel(id)),"SERVICE_UNAVAILABLE");observer.next("GAME_END",id,1);released(id,f.room().id());
        var detail=json.readTree(host.client.call("GET",history(id),null).body());
        assertThat(detail.path("finalSnapshot").path("endReason").asText()).isEqualTo("SERVER_INTERRUPTED");
        assertThat(detail.path("finalSnapshot").path("hasOfficialWinner").asBoolean()).isFalse();
        assertThat(detail.path("questions").get(0).path("answers").get(0).path("answerStatus").asText()).isEqualTo("ACCEPTED_UNSCORED");
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=?",Integer.class,id)).containsOnly(20);
    }
    @Test void startupCleanupRollbackStopsAfterThreeAttemptsAndOnlyBecomesReadyAfterCommit() {
        var f=fixture();long id=transactions.start(f.host().id(),f.room().id(),f.room().revision(),10,clock.sample().epochMs()).game().publicView().gameSessionId();gameIds.add(id);
        var startup=new GameStartupCleanup(transactions);AtomicInteger attempts=new AtomicInteger();
        doAnswer(inv -> {attempts.incrementAndGet();throw new TransientDataAccessResourceException("Controlled startup rollback after SQL");}).when(players).flush();
        assertThatThrownBy(() -> startup.run(new org.springframework.boot.DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class).hasMessageContaining("Start is disabled");
        assertThat(attempts.get()).isEqualTo(3);assertThat(startup.ready()).isFalse();
        assertThat(jdbc.queryForObject("select status from game_session where id=?",String.class,id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isEqualTo(4);
        reset(players);startup.run(new org.springframework.boot.DefaultApplicationArguments());assertThat(startup.ready()).isTrue();released(id,f.room().id());
    }
}
