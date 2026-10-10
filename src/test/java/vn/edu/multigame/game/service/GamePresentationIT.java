package vn.edu.multigame.game.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.quiz.enums.Option;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL/raw WS regression for the shared result window and fixed decision rule. */
class GamePresentationIT extends GameNetworkFixture {
    @Test void migrationUpdatesOnlyIdleRoomsAndLeavesLegacyGameSnapshotsByteIdentical() throws Exception {
        var f=fixture();var oldConfig=json.writeValueAsString(vn.edu.multigame.game.dto.GameplayRulesSnapshot.forGame(10,2300,5000));
        var ids=new LinkedHashMap<String,Long>();var snapshots=new LinkedHashMap<Long,String>();
        for(String status:List.of("DRAFT","WAITING","ACTIVE","CLOSED")) {
            var r=room(f.host(),f.quiz(),f.roster());ids.put(status,r.id());
            jdbc.update("update room set status=?,decision_duration_ms=5000 where id=?",status,r.id());
            if(status.equals("ACTIVE") || status.equals("CLOSED")) {
                boolean active=status.equals("ACTIVE");
                jdbc.update("insert into game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,config_snapshot,started_at_ms,finished_at_ms) values(?,?,?,'Legacy',?,?,?,10,?,1000,?)",
                    r.id(),f.quiz(),f.host().id(),active?"ACTIVE":"FINISHED",active?"DECISION":"FINISHED",active?null:"COMPLETED",oldConfig,active?null:2000L);
                long game=jdbc.queryForObject("select id from game_session where room_id=?",Long.class,r.id());gameIds.add(game);
                jdbc.update("insert into game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) values(?,?,?,'HOST','SPECTATOR','Legacy Host')",game,r.id(),f.host().id());
                snapshots.put(game,jdbc.queryForObject("select cast(config_snapshot as char) from game_session where id=?",String.class,game));
            }
        }
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/migration/V3__decision_seven_seconds.sql"))
            .execute(jdbc.getDataSource());
        for(var entry:ids.entrySet())assertThat(jdbc.queryForObject("select decision_duration_ms from room where id=?",Long.class,entry.getValue()))
            .isEqualTo(Set.of("DRAFT","WAITING").contains(entry.getKey())?7000L:5000L);
        for(var entry:snapshots.entrySet())assertThat(jdbc.queryForObject("select cast(config_snapshot as char) from game_session where id=?",String.class,entry.getKey())).isEqualTo(entry.getValue());
        assertThat(jdbc.queryForObject("select column_default from information_schema.columns where table_schema=database() and table_name='room' and column_name='decision_duration_ms'",String.class)).isEqualTo("7000");
    }
    @ParameterizedTest @ValueSource(longs={2300L,4100L})
    void legacyWaitingRoomStartsWithSevenSecondsAndPreservesConfiguredAnswerDuration(long duration) throws Exception {
        var f=fixture();
        jdbc.update("update room set decision_duration_ms=5000,question_duration_ms=? where id=?",duration,f.room().id());
        long id=start(f);
        var decision=runtime.snapshot(id,f.host().id());
        assertThat(decision.config().decisionDurationMs()).isEqualTo(7000L);
        assertThat(decision.remainingMs()).isEqualTo(7000L);
        assertThat(decision.question()).isNull();
        var opened=open(id,1);
        assertThat(opened.remainingMs()).isEqualTo(duration);
        expire(opened);
        var result=observer.next("QUESTION_RESULT",id,1);
        assertThat(result.results()).allSatisfy(r -> assertThat(r.answerTimeMs()).isEqualTo(duration));
        assertThat(result.remainingMs()).isEqualTo(1500L);
        assertThat(decision(id,2).remainingMs()).isEqualTo(7000L);
        assertThat(runtime.snapshot(id,f.roster().getFirst().id()).player().totalAnswerTimeMs()).isEqualTo(duration);
    }

    @Test void resultReconnectUsesRemainingSharedWindowAndOldTimersCannotAdvanceAgain() throws Exception {
        var f=fixture(); long id=start(f); var opened=open(id,1);
        scheduler.advance(clock.mono.get()+17);
        for(var account:f.roster()) runtime.acceptAnswer(id,account.id(),1,Option.D).get();
        var result=observer.next("QUESTION_RESULT",id,1);
        long deadline=result.deadlineEpochMs()-clock.epoch;
        waitUntil(() -> scheduler.tasks.stream().anyMatch(t -> !t.cancelled().get() && t.due()==deadline));
        var resultTimer=scheduler.tasks.stream().filter(t -> !t.cancelled().get() && t.due()==deadline).findFirst().orElseThrow();
        scheduler.advance(deadline-1);
        var wire=new Wire(f.roster().getFirst());
        var snapshot=wire.response(envelope("RECONNECT","GAME",id,null,Map.of())).path("payload");
        assertThat(snapshot.path("phase").asText()).isEqualTo("RESULT");
        assertThat(snapshot.path("remainingMs").asLong()).isEqualTo(1L);
        assertThat(snapshot.path("question").path("correctAnswer").asText()).isEqualTo("D");
        assertThat(snapshot.path("player").path("totalAnswerTimeMs").asLong()).isEqualTo(17L);
        scheduler.advance(deadline);
        var next=observer.next("DECISION_STARTED",id,2);
        assertThat(next.remainingMs()).isEqualTo(7000L);
        resultTimer.action().run();resultTimer.action().run();
        var current=wire.response(envelope("RECONNECT","GAME",id,null,Map.of())).path("payload");
        assertThat(current.path("questionIndex").asInt()).isEqualTo(2);
        assertThat(current.path("phase").asText()).isEqualTo("DECISION");
        assertThat(current.path("question").isNull()).isTrue();
        assertThat(current.path("player").path("totalAnswerTimeMs").asLong()).isEqualTo(17L);
        assertThat(observer.seen.stream().filter(e -> e.type().equals("QUESTION_RESULT") && e.snapshot().gameSessionId()==id)).hasSize(1);
    }

    @Test void hostSpectatorCanCancelDuringResultAndQueuedTransitionBecomesNoOp() throws Exception {
        var f=fixture(); long id=start(f);expire(open(id,1));
        var result=observer.next("QUESTION_RESULT",id,1);
        var host=new Wire(f.host());accepted(host.response(envelope("CANCEL_GAME","GAME",id,null,Map.of())));
        scheduler.advance(result.deadlineEpochMs()-clock.epoch);
        var finalState=runtime.snapshot(id,f.host().id());
        assertThat(finalState.endReason()).isEqualTo(EndReason.CANCELLED);
        assertThat(finalState.deadlineEpochMs()).isNull();
        assertThat(observer.seen.stream().filter(e -> e.type().equals("DECISION_STARTED") && e.snapshot().gameSessionId()==id && e.snapshot().questionIndex()==2)).isEmpty();
        assertThat(finalState.members().stream().filter(m -> m.score()!=null).map(m -> m.score())).containsOnly(19);
    }

    @Test void earlyTerminalCleanupCommitsBeforePresentationEndsAndNeverOpensAnotherDecision() throws Exception {
        var f=fixture(); long id=start(f);
        jdbc.update("update player_session set score=0 where game_session_id=? and user_id<>?",id,f.roster().getLast().id());
        jdbc.update("update room set decision_duration_ms=5000 where id=?",f.room().id());
        open(id,1);
        for(var account:f.roster())runtime.acceptAnswer(id,account.id(),1,account==f.roster().getLast()?Option.D:Option.A).get();
        var ended=observer.next("GAME_END",id,1);
        assertThat(ended.endReason()).isEqualTo(EndReason.ONE_SURVIVOR);
        assertThat(ended.remainingMs()).isEqualTo(1500L);
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select decision_duration_ms from room where id=?",Long.class,f.room().id())).isEqualTo(7000L);
        assertThat(ended.config().decisionDurationMs()).isEqualTo(7000L);
        scheduler.advance(ended.deadlineEpochMs()-clock.epoch);
        assertThat(runtime.snapshot(id,f.host().id()).deadlineEpochMs()).isNull();
        assertThat(observer.seen.stream().filter(e -> e.type().equals("DECISION_STARTED") && e.snapshot().gameSessionId()==id && e.snapshot().questionIndex()>1)).isEmpty();
    }
}
