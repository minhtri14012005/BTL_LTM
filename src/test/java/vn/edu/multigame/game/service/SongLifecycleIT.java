package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.*;
import vn.edu.multigame.game.enums.GameMode;
import vn.edu.multigame.questionbank.service.QuestionVideoStore;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL/HTTP/cookie/raw WS/files. Fake clock and latch only control timing/failure. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000",
    "quiz.videos.directory=./target/task22-test-videos"
})
class SongLifecycleIT extends GameNetworkFixture {
    record Setup(Fixture fixture,long songSet,List<String> refs,Client owner) {}
    JsonNode upload(Client owner,String endpoint,int variant) throws Exception {
        byte[] video;try(var input=getClass().getResourceAsStream("/quiz/"+(variant==0?"song-av.mp4":"song-av2.mp4"))){video=input.readAllBytes();}
        var response=uploadBytes(owner,endpoint,video,"video/mp4");assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return json.readTree(response.body());
    }
    HttpResponse<String> uploadBytes(Client owner,String endpoint,byte[] video,String type) throws Exception {
        String b="task22-owned-boundary";var bytes=new ByteArrayOutputStream();
        bytes.write(("--"+b+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"video.mp4\"\r\nContent-Type: "+type+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));bytes.write(video);bytes.write(("\r\n--"+b+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return owner.http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+endpoint)).header("X-CSRF-TOKEN",owner.csrf).header("Content-Type","multipart/form-data; boundary="+b).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    QuestionRequest songQuestion(String content,String ref,String answer){return new QuestionRequest(content,null,null,null,List.of(answer),null,null,null,ref);}
    HttpResponse<byte[]> range(Client client,String endpoint,String value)throws Exception {
        return client.http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+endpoint)).header("Range",value).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    Setup setup(boolean mixed) throws Exception {
        var host=account();var roster=List.of(account(),account(),account());var owner=new Client(host);
        var refs=List.of(upload(owner,"/api/quizzes/videos/drafts",0).path("mediaRef").asText(),upload(owner,"/api/quizzes/videos/drafts",1).path("mediaRef").asText());
        var set=quizService.create(host.auth(),new CreateQuestionBankRequest("Đoán bài hát",Visibility.PRIVATE,List.of(songQuestion("Clip một",refs.get(0),"bắt cá"),songQuestion("Clip hai",refs.get(1),"bắt cá")),GameMode.SONG));quizIds.add(set.id());
        var plan=new ArrayList<RoomStageRequest>();long quiz=0;
        if(mixed) {quiz=quiz(account(),1);plan.add(new RoomStageRequest(GameMode.QUIZ,quiz,1,1000L));}
        plan.add(new RoomStageRequest(GameMode.SONG,set.id(),2,1000L));
        if(mixed) {var riddle=quizService.create(host.auth(),new CreateQuestionBankRequest("Đố mẹo",Visibility.PRIVATE,List.of(new QuestionRequest("Đố?",null,null,null,List.of("bắt cá"))),GameMode.RIDDLE));quizIds.add(riddle.id());plan.add(new RoomStageRequest(GameMode.RIDDLE,riddle.id(),1,1000L));}
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"Song lifecycle",3,null,Participation.SPECTATOR,plan))).snapshot();roomIds.add(r.id());long rev=r.revision();
        roomOps.execute(host.auth(),r.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",rev),null,()->{},()->rooms.open(host.id(),r.id(),rev),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),r.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),()->{},()->rooms.join(p.id(),r.id(),r.roomCode(),Participation.PLAYER),(a,b)->{});
        return new Setup(new Fixture(host,roster,quiz,rooms.get(host.id(),r.id())),set.id(),refs,owner);
    }
    List<Wire> online(Fixture f) throws Exception {var result=new ArrayList<Wire>();for(var p:f.roster()){var w=new Wire(p);w.subscribe(f);result.add(w);}return result;}
    long start2(Fixture f) throws Exception {
        var response=new Client(f.host()).call("POST","/api/rooms/"+f.room().id()+"/start",new StartGameRequest(UUID.randomUUID().toString(),f.room().revision(),f.room().stages().stream().mapToInt(s->s.questionCount()).sum()));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);long id=json.readTree(response.body()).path("gameSessionId").asLong();gameIds.add(id);installed.add(id);observer.next("INTRO_STARTED",id,1);return id;
    }
    void ready(List<Wire> ws,long id,int index) throws Exception {for(var w:ws)accepted(w.response(command("CONTINUE",id,index,Map.of())));}
    void advance(GameSnapshot phase) {long due=phase.deadlineEpochMs()-clock.epoch;waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get()&&t.due()==due));scheduler.advance(Math.max(due,clock.mono.get()));}
    String url(Setup s,int image,long game) {return "/api/quizzes/"+s.songSet()+"/videos/"+s.refs().get(image).substring(7)+"?gameSessionId="+game;}
    @Test void mixedStagesVideoRangePrivacySnapshotReconnectAndScoring() throws Exception {
        var s=setup(true);var f=s.fixture();var ws=online(f);var outsider=new Client(account());long id=start2(f);
        assertThat(ws.get(0).client.call("GET",url(s,0,id),null).statusCode()).isEqualTo(403);
        assertThat(ws.get(0).client.call("GET","/api/games/history/"+id,null).statusCode()).isEqualTo(409);
        ready(ws,id,1);advance(observer.next("DECISION_STARTED",id,1));observer.next("QUESTION_START",id,1);clock.mono.addAndGet(100);
        for(var w:ws)accepted(w.response(command("ANSWER",id,1,Map.of("option","D"))));advance(observer.next("QUESTION_RESULT",id,1));observer.next("INTRO_STARTED",id,2);
        ready(ws,id,2);var opened=observer.next("QUESTION_START",id,2);
        assertThat(opened.question().payload().get("mediaRef")).isEqualTo(s.refs().get(0));assertThat(opened.question().payload()).containsOnlyKeys("mediaRef","openedAtMs");assertThat(opened.player()).isNull();
        assertThat(ws.get(0).client.call("GET",url(s,0,id),null).statusCode()).isEqualTo(200);
        var partial=range(ws.get(0).client,url(s,0,id),"bytes=0-31");assertThat(partial.statusCode()).isEqualTo(206);assertThat(partial.body()).hasSize(32);assertThat(partial.headers().firstValue("Content-Range").orElseThrow()).startsWith("bytes 0-31/");
        assertThat(range(ws.get(0).client,url(s,0,id),"bytes=99999999-").statusCode()).isEqualTo(416);
        assertThat(opened.question().payload().get("openedAtMs")).isEqualTo(clock.epoch+clock.mono.get());
        assertThat(ws.get(0).client.call("GET",url(s,1,id),null).statusCode()).isEqualTo(403);
        assertThat(outsider.call("GET",url(s,0,id),null).statusCode()).isEqualTo(403);
        String newRef=upload(s.owner(),"/api/quizzes/"+s.songSet()+"/videos",1).path("mediaRef").asText();
        quizService.edit(f.host().auth(),s.songSet(),new EditQuestionBankRequest("Changed source",Visibility.PRIVATE,0L,List.of(songQuestion("Changed hint",newRef,"khác")),GameMode.SONG));
        var snap=ws.get(0).response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(snap);assertThat(snap.path("payload").path("question").path("content").asText()).isEqualTo("Clip một");assertThat(snap.path("payload").path("deadlineEpochMs").asLong()).isEqualTo(opened.deadlineEpochMs());
        assertThat(ws.get(0).client.call("GET","/api/quizzes/"+s.songSet()+"/videos/"+newRef.substring(7)+"?gameSessionId="+id,null).statusCode()).isEqualTo(403);
        clock.mono.addAndGet(100);var saved=command("ANSWER",id,2,Map.of("text"," BẮT   CÁ "));var ack=ws.get(0).response(saved);accepted(ack);assertThat(ack.toString()).doesNotContain("CORRECT","acceptedAnswers");
        accepted(ws.get(1).response(command("ANSWER",id,2,Map.of("text","bắt cá"))));accepted(ws.get(2).response(command("ANSWER",id,2,Map.of("text","bat ca"))));
        var result=observer.next("QUESTION_RESULT",id,2);assertThat(result.results().stream().map(r->r.scoreDelta())).containsExactly(10,10,0);assertThat(result.question().payload()).containsEntry("acceptedAnswers",List.of("bắt cá"));assertThat(ws.get(0).response(saved)).isEqualTo(ack);advance(result);
        var q=observer.next("QUESTION_START",id,3);Path blob=Path.of("target/task22-test-videos",Long.toString(s.songSet()),s.refs().get(1).substring(7)+".mp4");Path backup=blob.resolveSibling(blob.getFileName()+".task22-backup");
        Files.move(blob,backup);try {
            assertThat(ws.get(0).client.call("GET",url(s,1,id),null).statusCode()).isEqualTo(404);clock.mono.addAndGet(100);
            var re=ws.get(0).response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(re);assertThat(re.path("payload").path("phase").asText()).isEqualTo("QUESTION_OPEN");assertThat(re.path("payload").path("deadlineEpochMs").asLong()).isEqualTo(q.deadlineEpochMs());
            for(int p=0;p<2;p++)accepted(ws.get(p).response(command("ANSWER",id,3,Map.of("text","bắt cá"))));
            clock.mono.set(q.deadlineEpochMs()-clock.epoch);rejected(ws.get(2).response(command("ANSWER",id,3,Map.of("text","bắt cá"))),"QUESTION_CLOSED");advance(q);
            var scored=observer.next("QUESTION_RESULT",id,3);assertThat(scored.results().stream().map(r->r.scoreDelta())).containsExactly(20,20,0);advance(scored);
        } finally {Files.move(backup,blob);}
        observer.next("INTRO_STARTED",id,4);ready(ws,id,4);observer.next("QUESTION_START",id,4);clock.mono.addAndGet(100);for(var w:ws)accepted(w.response(command("ANSWER",id,4,Map.of("text","bắt cá"))));observer.next("GAME_END",id,4);
        assertThat(ws.get(0).response(saved)).isEqualTo(ack);assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,id)).containsExactly(60,60,30);
        assertThat(jdbc.queryForList("select total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",Long.class,id)).containsExactly(400L,400L,200L);
        quizService.delete(f.host().auth(),s.songSet(),1);var history=json.readTree(ws.get(0).client.call("GET","/api/games/history/"+id,null).body());
        assertThat(history.path("questions").get(1).path("payload").path("mediaRef").asText()).isEqualTo(s.refs().get(0));assertThat(history.toString()).contains("Clip một","bắt cá").doesNotContain("Changed hint");assertThat(ws.get(0).client.call("GET",url(s,0,id),null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
    }
    @Test void publicMetadataDraftIsolationAndUnauthenticatedRange()throws Exception {
        var owner=account();var client=new Client(owner);var other=new Client(account());
        var response=upload(client,"/api/quizzes/videos/drafts",0);String ref=response.path("mediaRef").asText();
        var set=quizService.create(owner.auth(),new CreateQuestionBankRequest("Public video",Visibility.PUBLIC,List.of(songQuestion("Nghe rồi đoán",ref,"Tên bài hát")),GameMode.SONG));quizIds.add(set.id());
        var publicView=other.call("GET","/api/quizzes/"+set.id(),null);assertThat(publicView.statusCode()).isEqualTo(200);assertThat(publicView.body()).doesNotContain("acceptedAnswers","mediaRef","questions");
        assertThat(range(other,"/api/quizzes/"+set.id()+"/videos/"+ref.substring(7),"bytes=0-31").statusCode()).isEqualTo(403);
        assertThat(client.call("GET","/api/quizzes/"+set.id()+"/videos/bad-hash",null).statusCode()).isEqualTo(400);
        var unauth=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/quizzes/videos/drafts/"+ref.substring(7))).header("Range","bytes=0-31").GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(unauth.statusCode()).isEqualTo(401);
        assertThat(range(client,"/api/quizzes/videos/drafts/"+ref.substring(7),"bytes=-16").statusCode()).isEqualTo(206);
        Path blob=Path.of("target/task22-test-videos",Long.toString(set.id()),ref.substring(7)+".mp4"),backup=blob.resolveSibling(blob.getFileName()+".task22-owned-integrity-backup");
        Files.move(blob,backup);
        try {Files.write(blob,new byte[]{0});var corrupt=client.call("GET","/api/quizzes/"+set.id()+"/videos/"+ref.substring(7),null);assertThat(corrupt.statusCode()).isEqualTo(503);assertThat(json.readTree(corrupt.body()).path("code").asText()).isEqualTo("VIDEO_UNAVAILABLE");}
        finally {Files.deleteIfExists(blob);Files.move(backup,blob);}
        assertThatThrownBy(()->quizService.create(owner.auth(),new CreateQuestionBankRequest("Missing video",Visibility.PUBLIC,List.of(songQuestion("?",null,"name")),GameMode.SONG))).isInstanceOf(vn.edu.multigame.questionbank.service.QuestionBankFailure.class);
    }
    @Test void songTextRollbackConcurrentReplayAndCancelDoNotPublishAnswersOrCacheBeforeCommit() throws Exception {
        var s=setup(false);var f=s.fixture();var ws=online(f);
        assertThat(uploadBytes(s.owner(),"/api/quizzes/videos/drafts",new byte[32],"video/mp4").statusCode()).isEqualTo(400);
        try(var input=getClass().getResourceAsStream("/quiz/song-silent.mp4")){assertThat(uploadBytes(s.owner(),"/api/quizzes/videos/drafts",input.readAllBytes(),"video/mp4").statusCode()).isEqualTo(400);}
        assertThat(uploadBytes(ws.get(0).client,"/api/quizzes/"+s.songSet()+"/videos",new byte[1],"video/mp4").statusCode()).isEqualTo(403);
        assertThat(ws.get(0).client.call("GET","/api/quizzes/"+s.songSet(),null).statusCode()).isEqualTo(404);
        assertThat(ws.get(0).client.call("GET","/api/quizzes/videos/drafts/"+s.refs().get(0).substring(7),null).statusCode()).isEqualTo(404);
        assertThat(uploadBytes(s.owner(),"/api/quizzes/videos/drafts",new byte[QuestionVideoStore.MAX_BYTES+1],"video/mp4").statusCode()).isEqualTo(413);
        long id=start2(f);ready(ws,id,1);observer.next("QUESTION_START",id,1);
        AtomicInteger attempts=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(inv->{var result=inv.callRealMethod();if(attempts.incrementAndGet()==1){entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Barrier");throw new TransientDataAccessResourceException("Song answer rollback");}return result;}).when(transactions).acceptTyped(eq(id),eq(f.roster().get(0).id()),eq(1),isNull(),any(),anyLong(),anyLong());
        var cmd=command("ANSWER",id,1,Map.of("text","bắt cá"));ws.get(0).send(cmd);
        try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();ws.get(0).send(cmd);release.countDown();waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get()&&t.delay()==100));assertThat(answerCount(id)).isZero();scheduler.retry(100);
            var ack=ws.get(0).response(cmd.path("requestId").asText(),1);accepted(ack);assertThat(ws.get(0).response(cmd.path("requestId").asText(),2)).isEqualTo(ack);assertThat(attempts).hasValue(2);
            rejected(ws.get(0).response(command("ANSWER",id,1,Map.of("text","khác"))),"ALREADY_ANSWERED");var mismatch=cmd.deepCopy();mismatch.set("payload",json.valueToTree(Map.of("text","khác")));rejected(ws.get(0).response(mismatch),"INVALID_REQUEST_ID");
            var re=ws.get(0).response(envelope("RECONNECT","GAME",id,null,Map.of()));accepted(re);assertThat(re.path("payload").path("player").path("submittedAnswer").path("text").asText()).isEqualTo("bắt cá");
            assertThat(s.owner().call("POST","/api/games/"+id+"/cancel",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(200);observer.next("GAME_END",id,1);
            assertThat(ws.get(0).response(cmd)).isEqualTo(ack);var history=json.readTree(s.owner().call("GET","/api/games/history/"+id,null).body());assertThat(history.toString()).doesNotContain("acceptedAnswers","matchingPolicy");
            assertThat(history.path("questions").get(0).path("answers").get(0).path("answerStatus").asText()).isEqualTo("ACCEPTED_UNSCORED");assertThat(answerCount(id)).isEqualTo(1);
        } finally {release.countDown();}
    }
}
