package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.edu.multigame.achievements.service.AwardReadService;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.realtime.session.RoomBoundary;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.dto.response.RoomResponse;
import vn.edu.multigame.room.enums.Participation;
import static org.assertj.core.api.Assertions.*;

/** Existing transactions/boundary on real MySQL; never replaces lifecycle or invents snapshots. */
class SameRoomReplayIT extends AdvancedAwardFixture {
    @Autowired AwardReadService awardReads;
    @Autowired GameHistoryService history;

    RoomResponse room(Match m){return rooms.get(m.fixture().host().id(),m.fixture().room().id());}
    RoomBoundary.Receipt start(Match m,RoomResponse ready,String request){
        return roomOps.execute(m.fixture().host().auth(),ready.id(),request,"START_GAME",
            Map.of("revision",ready.revision(),"questionCount",ready.stages().stream().mapToInt(RoomResponse.Stage::questionCount).sum()),null,()->{},
            ()->transactions.startMultimodeSnapshot(m.fixture().host().id(),ready.id(),ready.revision(),System.currentTimeMillis()),(a,b)->{});
    }
    long pack(Account owner,GameMode mode,String title){
        var q=mode==GameMode.QUIZ?new QuestionRequest(title,Map.of(vn.edu.multigame.quiz.enums.Option.A,"A",vn.edu.multigame.quiz.enums.Option.B,"B",vn.edu.multigame.quiz.enums.Option.C,"C",vn.edu.multigame.quiz.enums.Option.D,"D"),vn.edu.multigame.quiz.enums.Option.D,null):new QuestionRequest(title,null,null,null,List.of("yes"));
        long id=quizService.create(owner.auth(),new CreateQuestionBankRequest(title,Visibility.PRIVATE,Collections.nCopies(10,q),mode)).id();quizIds.add(id);return id;
    }
    void plan(Match m,GameMode mode,long bank){
        var r=room(m);var config=new RoomConfigRequest(null,"Same retained room",8,null,Participation.SPECTATOR,List.of(new RoomStageRequest(mode,bank,10,1000L)));
        roomOps.execute(m.fixture().host().auth(),r.id(),UUID.randomUUID().toString(),"EDIT_ROOM",Map.of("revision",r.revision(),"config",config),null,()->{},
            ()->rooms.edit(m.fixture().host().id(),r.id(),r.revision(),config),(a,b)->{});
    }
    Match next(Match old,List<Account> roster,GameMode mode){
        long id=start(old,room(old),UUID.randomUUID().toString()).gameSessionId();gameIds.add(id);
        return new Match(new Fixture(old.fixture().host(),roster,0,room(old)),id,List.of(mode),List.of(10));
    }
    void fresh(Match m,int spins,boolean star){
        assertThat(players.findByGameSessionId(m.game())).hasSize(3).allSatisfy(p->{
            assertThat(p.getScore()).isZero();assertThat(p.getWinStreak()).isZero();assertThat(p.getLoseStreak()).isZero();
            assertThat(p.getHasMomentum()).isFalse();assertThat(p.getHasRecovery()).isFalse();assertThat(p.getCurrentSpin()).isNull();assertThat(p.getStarSelected()).isFalse();
            assertThat(p.getRemainingSpins()).isEqualTo(spins);assertThat(p.getStarAvailable()).isEqualTo(star);assertThat(p.getTotalCorrectAnswerTimeMs()).isZero();assertThat(p.getFinalRank()).isNull();
        });
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=?",Long.class,m.game())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from game_question where game_session_id=? and (opened_at_ms is not null or scored_at_ms is not null or participation_captured<>0)",Long.class,m.game())).isZero();
    }
    void complete(Match m){for(int i=1;i<=m.count();i++)question(m,i,Set.of(m.ids().getFirst()),Set.of());}

    @Test void threeGamesResetStateAndKeepHistoryAwardsDespitePackAndRosterChange(){
        var first=match(List.of(GameMode.QUIZ,GameMode.RIDDLE),List.of(10,2));fresh(first,1,true);
        open(first,1,()->{transactions.useSpin(first.game(),first.ids().getFirst(),1,SpinEffect.BONUS);transactions.useStar(first.game(),first.ids().getFirst(),1);});
        answerAndScore(first,1,Set.of(first.ids().getFirst()),Set.of());
        for(int i=2;i<=first.count();i++)question(first,i,Set.of(first.ids().getFirst()),Set.of());
        awardReconciler.dispatch(first.game());var ledger=awardReads.read(first.ids().getFirst(),first.game()).awards();assertThat(ledger).isNotEmpty();
        var past=history.detail(first.ids().getFirst(),first.game()).questions();
        assertThat(room(first).status().name()).isEqualTo("WAITING");assertThat(room(first).members()).isEqualTo(first.fixture().room().members());
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Long.class,first.game())).isZero();

        var departed=first.fixture().roster().get(2);var replacement=account();long rid=room(first).id();
        roomOps.execute(departed.auth(),rid,UUID.randomUUID().toString(),"LEAVE_ROOM",Map.of(),null,()->{},()->rooms.leave(departed.id(),rid),(a,b)->{});
        var ready=room(first);roomOps.execute(replacement.auth(),rid,UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",ready.roomCode(),"participation","PLAYER"),ready.roomCode(),()->{},()->rooms.join(replacement.id(),rid,ready.roomCode(),Participation.PLAYER),(a,b)->{});
        var roster=List.of(first.fixture().roster().get(0),first.fixture().roster().get(1),replacement);
        long riddle=pack(first.fixture().host(),GameMode.RIDDLE,"Game two pack");plan(first,GameMode.RIDDLE,riddle);
        var second=next(first,roster,GameMode.RIDDLE);fresh(second,0,false);
        assertThat(jdbc.queryForList("select user_id from game_member where game_session_id=?",Long.class,second.game())).doesNotContain(departed.id());
        assertThat(jdbc.queryForList("select source_quiz_id from game_stage where game_session_id=?",Long.class,second.game())).containsOnly(riddle);
        complete(second);awardReconciler.dispatch(second.game());assertThat(room(second).status().name()).isEqualTo("WAITING");
        long quiz=pack(first.fixture().host(),GameMode.QUIZ,"Game three pack");plan(second,GameMode.QUIZ,quiz);
        var third=next(second,roster,GameMode.QUIZ);fresh(third,1,true);complete(third);awardReconciler.dispatch(third.game());
        assertThat(Set.of(first.game(),second.game(),third.game())).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Long.class,rid)).isEqualTo(3);
        assertThat(room(third).status().name()).isEqualTo("WAITING");assertThat(room(third).members()).isEqualTo(second.fixture().room().members());
        assertThat(history.detail(first.ids().getFirst(),first.game()).questions()).isEqualTo(past);
        assertThat(history.detail(departed.id(),first.game()).questions()).isNotEmpty();
        assertThat(awardReads.read(first.ids().getFirst(),first.game()).awards()).isEqualTo(ledger);
        var current=room(third);rooms.close(first.fixture().host().id(),rid,current.revision());
        assertThat(awardReads.read(first.ids().getFirst(),first.game()).awards()).isEqualTo(ledger);
    }

    @Test void concurrentReplayStartAndStaleReceiptNeverCreateAnotherGame()throws Exception{
        var first=match(List.of(GameMode.RIDDLE),List.of(10));complete(first);
        var ready=room(first);String request=UUID.randomUUID().toString();
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);
            Callable<RoomBoundary.Receipt> submit=()->{assertThat(gate.await(5,TimeUnit.SECONDS)).isTrue();return start(first,ready,request);};
            var a=pool.submit(submit);var b=pool.submit(submit);gate.countDown();var one=a.get(10,TimeUnit.SECONDS);var two=b.get(10,TimeUnit.SECONDS);
            assertThat(one).isEqualTo(two);long id=one.gameSessionId();gameIds.add(id);
            var second=new Match(first.fixture(),id,List.of(GameMode.RIDDLE),List.of(10));complete(second);
            assertThat(start(first,ready,request)).isEqualTo(one);
            assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Long.class,ready.id())).isEqualTo(2);
            assertThat(room(first).status().name()).isEqualTo("WAITING");
        }
    }
}
