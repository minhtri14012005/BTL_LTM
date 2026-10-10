package vn.edu.multigame.game.service;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.game.dto.request.StartGameRequest;
import vn.edu.multigame.game.dto.response.GameSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.*;
import vn.edu.multigame.game.enums.GameMode;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import static org.assertj.core.api.Assertions.*;

/** Integration boundaries not covered by the per-mode fixtures: a maximum-size
 * single-mode game and persisted correct-only ties. Real MySQL/HTTP/raw WS;
 * inherited controllable clock only determines exact expected elapsed times. */
class MultimodeIntegrationIT extends GameNetworkFixture {
    void advanceWindow(GameSnapshot phase) {
        long due=phase.deadlineEpochMs()-clock.epoch;
        waitUntil(()->scheduler.tasks.stream().anyMatch(t->!t.cancelled().get() && t.due()==due));
        scheduler.advance(Math.max(due,clock.mono.get()));
    }

    @Test void fiftyQuestionSingleModeKeepsCorrectOnlyTiesAndTerminalReplay() throws Exception {
        var host=account();var roster=List.of(account(),account(),account());
        var item=new QuestionRequest("Đố mẹo: có chân mà không đi?",null,null,null,List.of("bàn","cái bàn"));
        var source=quizService.create(host.auth(),new CreateQuestionBankRequest("Task24 fifty",Visibility.PRIVATE,Collections.nCopies(50,item),GameMode.RIDDLE));
        quizIds.add(source.id());
        var room=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),
            new RoomConfigRequest(null,"Task24 maximum",3,null,Participation.SPECTATOR,
                List.of(new RoomStageRequest(GameMode.RIDDLE,source.id(),50,1000L))))).snapshot();
        roomIds.add(room.id());long revision=room.revision();
        roomOps.execute(host.auth(),room.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",revision),null,()->{},
            ()->rooms.open(host.id(),room.id(),revision),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),room.id(),UUID.randomUUID().toString(),"JOIN_ROOM",
            Map.of("roomCode",room.roomCode(),"participation","PLAYER"),room.roomCode(),()->{},
            ()->rooms.join(p.id(),room.id(),room.roomCode(),Participation.PLAYER),(a,b)->{});
        var fixture=new Fixture(host,roster,0,rooms.get(host.id(),room.id()));
        var sockets=new ArrayList<Wire>();for(var p:roster){var w=new Wire(p);w.subscribe(fixture);sockets.add(w);}
        var response=new Client(host).call("POST","/api/rooms/"+room.id()+"/start",
            new StartGameRequest(UUID.randomUUID().toString(),fixture.room().revision(),50));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        long game=json.readTree(response.body()).path("gameSessionId").asLong();gameIds.add(game);installed.add(game);
        observer.next("INTRO_STARTED",game,1);
        for(var w:sockets)accepted(w.response(command("CONTINUE",game,1,Map.of())));
        JsonNode firstCommand=null,firstAck=null;
        for(int index=1;index<=50;index++) {
            var opened=observer.next("QUESTION_START",game,index);
            assertThat(opened.question().mode()).isEqualTo(GameMode.RIDDLE);
            assertThat(opened.question().payload()).isEmpty();
            var player=runtime.snapshot(game,roster.getFirst().id()).player();
            assertThat(player.remainingSpins()).isZero();assertThat(player.starAvailable()).isFalse();
            assertThat(player.state()).isEqualTo(PlayerState.PLAYING);
            long started=clock.mono.get();
            boolean correct=index==1 || index==50;
            // Wrong answers have deliberately different timing/order; none of
            // these times may change the final correct-only tie.
            int[] order=correct?new int[]{0,2,1}:new int[]{1,0,2};
            long[] times=correct?new long[]{100,200,100}:new long[]{700,400,800};
            for(int p:order) {
                clock.mono.set(started+times[p]);
                var cmd=command("ANSWER",game,index,Map.of("text",correct?" CÁI  BÀN ":"sai"));
                var ack=sockets.get(p).response(cmd);accepted(ack);
                assertThat(ack.toString()).doesNotContain("acceptedAnswers","CORRECT","WRONG");
                if(index==1 && p==0){firstCommand=cmd;firstAck=ack;}
            }
            var result=observer.next("QUESTION_RESULT",game,index);
            int expectedDelta=correct?(index==50?20:10):0;
            assertThat(result.results()).allSatisfy(r->assertThat(r.scoreDelta()).isEqualTo(expectedDelta));
            if(index<50)advanceWindow(result);
        }
        observer.next("GAME_END",game,50);
        var ended=runtime.snapshot(game,host.id());
        assertThat(ended.endReason()).isEqualTo(EndReason.COMPLETED);
        assertThat(ended.hasOfficialWinner()).isTrue();
        assertThat(ended.winners()).containsExactlyInAnyOrder(roster.get(0).id(),roster.get(2).id());
        assertThat(jdbc.queryForList("select score from player_session where game_session_id=? order by user_id",Integer.class,game)).containsExactly(30,30,30);
        assertThat(jdbc.queryForList("select total_correct_answer_time_ms from player_session where game_session_id=? order by user_id",Long.class,game)).containsExactly(200L,400L,200L);
        assertThat(jdbc.queryForList("select final_rank from player_session where game_session_id=? order by user_id",Integer.class,game)).containsExactly(1,3,1);
        assertThat(answerCount(game)).isEqualTo(150);
        assertThat(jdbc.queryForObject("select count(*) from player_session where game_session_id=? and player_state='ELIMINATED'",Integer.class,game)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,game)).isZero();
        assertThat(jdbc.queryForObject("select status from room where id=?",String.class,room.id())).isEqualTo("WAITING");
        assertThat(observer.committed).doesNotContain(false);
        assertThat(sockets.getFirst().response(firstCommand)).isEqualTo(firstAck);
        var history=sockets.getFirst().client.call("GET","/api/games/history/"+game,null);
        assertThat(history.statusCode()).as(history.body()).isEqualTo(200);
        var detail=json.readTree(history.body());assertThat(detail.path("questions")).hasSize(50);
        assertThat(detail.path("finalSnapshot").path("player").path("totalCorrectAnswerTimeMs").asLong()).isEqualTo(200);
        System.out.println("TASK24_MAXIMUM: game="+game+", one RIDDLE stage,50 questions/150 answers, score30/30/30, correct-time200/400/200, rank1/3/1");
    }
}
