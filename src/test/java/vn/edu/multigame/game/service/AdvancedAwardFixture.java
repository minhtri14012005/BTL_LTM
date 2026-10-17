package vn.edu.multigame.game.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import vn.edu.multigame.game.dto.TypedAnswer;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.realtime.session.RoomBoundary;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;

/** Real persisted mode snapshots/OPEN participation/scoring, reusable evidence and award fixture. */
abstract class AdvancedAwardFixture extends GameLifecycleFixture {
    @Autowired RoomBoundary boundary;
    record Match(Fixture fixture,long game,List<GameMode> modes,List<Integer> counts) {
        List<Long> ids(){return fixture.roster().stream().map(Account::id).toList();}
        int count(){return counts.stream().mapToInt(Integer::intValue).sum();}
    }
    Match match(List<GameMode> modes,List<Integer> counts){
        var host=account();var roster=List.of(account(),account(),account());var plan=new ArrayList<RoomStageRequest>();
        for(int i=0;i<modes.size();i++){
            var mode=modes.get(i);var q=mode==GameMode.QUIZ?new QuestionRequest("Question",Map.of(Option.A,"A",Option.B,"B",Option.C,"C",Option.D,"D"),Option.D,null):
                mode==GameMode.CLUES?new QuestionRequest("Question",null,null,null,List.of("yes"),null,null,null,null,List.of(new HintRequest(0L,"Hint"))):
                new QuestionRequest("Question",null,null,null,List.of("yes"));
            long bank=quizService.create(host.auth(),new CreateQuestionBankRequest("Advanced fixture",Visibility.PRIVATE,Collections.nCopies(counts.get(i),q),mode)).id();quizIds.add(bank);
            plan.add(new RoomStageRequest(mode,bank,counts.get(i),1000L));
        }
        var room=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(null,"Advanced awards",8,null,Participation.SPECTATOR,plan))).snapshot();roomIds.add(room.id());long revision=room.revision();
        roomOps.execute(host.auth(),room.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",revision),null,()->{},()->rooms.open(host.id(),room.id(),revision),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),room.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",room.roomCode(),"participation","PLAYER"),room.roomCode(),()->{},()->rooms.join(p.id(),room.id(),room.roomCode(),Participation.PLAYER),(a,b)->{});
        var ready=rooms.get(host.id(),room.id());long game=boundary.read(RoomBoundary.Scope.room(room.id()),()->transactions.startMultimodeSnapshot(host.id(),room.id(),ready.revision(),System.currentTimeMillis())).gameSessionId();gameIds.add(game);
        return new Match(new Fixture(host,roster,0,ready),game,List.copyOf(modes),List.copyOf(counts));
    }
    void open(Match m,int index){
        open(m,index,()->{});
    }
    void open(Match m,int index,Runnable decision){
        long at=jdbc.queryForObject("select started_at_ms from game_session where id=?",Long.class,m.game())+index*2000L;
        var row=jdbc.queryForMap("select mode,stage_question_index from game_question where game_session_id=? and order_index=?",m.game(),index);
        if(((Number)row.get("stage_question_index")).intValue()==1)transactions.open(m.game(),index,Phase.INTRO,d->PhaseWindow.open(index,Phase.INTRO,index*4L,0,at,d));
        if(row.get("mode").equals("QUIZ"))transactions.open(m.game(),index,Phase.DECISION,d->PhaseWindow.open(index,Phase.DECISION,index*4L+1,0,at,d));
        decision.run();
        transactions.open(m.game(),index,Phase.QUESTION_OPEN,d->PhaseWindow.open(index,Phase.QUESTION_OPEN,index*4L+2,0,at,d));
    }
    CommittedGame answerAndScore(Match m,int index,Set<Long> correct,Set<Long> afk){
        long at=jdbc.queryForObject("select opened_at_ms from game_question where game_session_id=? and order_index=?",Long.class,m.game(),index);
        String mode=jdbc.queryForObject("select mode from game_question where game_session_id=? and order_index=?",String.class,m.game(),index);
        var allowed=jdbc.queryForList("select p.user_id from game_question_participation qp join player_session p on p.id=qp.player_session_id join game_question q on q.id=qp.game_question_id where q.game_session_id=? and q.order_index=?",Long.class,m.game(),index);
        for(long user:m.ids())if(allowed.contains(user) && !afk.contains(user)){
            if(mode.equals("QUIZ"))transactions.accept(m.game(),user,index,correct.contains(user)?Option.D:Option.A,at+100,100);
            else transactions.acceptTyped(m.game(),user,index,null,new TypedAnswer(correct.contains(user)?"yes":"wrong",null),at+100,100);
        }
        transactions.transition(m.game(),index,Phase.QUESTION_OPEN,Phase.QUESTION_CLOSED);transactions.transition(m.game(),index,Phase.QUESTION_CLOSED,Phase.SCORING);return transactions.score(m.game(),index,at+1000);
    }
    CommittedGame question(Match m,int index,Set<Long> correct,Set<Long> afk){open(m,index);return answerAndScore(m,index,correct,afk);}
}
