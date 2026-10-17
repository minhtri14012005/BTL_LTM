package vn.edu.multigame.game.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.edu.multigame.achievements.repository.StageRankEvidenceRepository;
import vn.edu.multigame.game.enums.*;
import static org.assertj.core.api.Assertions.*;

/** Gate P04 BEFORE enabling Comeback: compare historical reconstruction to committed Server state. */
class StageRankEvidenceIT extends AdvancedAwardFixture {
    @Autowired StageRankEvidenceRepository evidence;
    @Test void quizBoundaryWithSpinStarFloorAndAfkMatchesServerBeforeAndAfterLaterStage(){
        var m=match(List.of(GameMode.QUIZ,GameMode.RIDDLE),List.of(10,6));var ids=m.ids();CommittedGame atBoundary=null;
        for(int i=1;i<=10;i++){
            int index=i;open(m,i,()->{if(index==1){transactions.useSpin(m.game(),ids.get(0),index,SpinEffect.BONUS);transactions.useStar(m.game(),ids.get(0),index);}});
            atBoundary=answerAndScore(m,i,Set.of(ids.get(0)),Set.of(ids.get(2)));
        }
        var first=evidence.read(m.game(),ids);assertThat(first.firstStageEndQuestion()).isEqualTo(10);
        for(var standing:first.firstStage()){
            var state=atBoundary.publicView().members().stream().filter(p->p.userId()==standing.userId()).findFirst().orElseThrow();
            assertThat(standing.score()).isEqualTo(state.score());assertThat(standing.correctTimeMs()).isEqualTo(state.totalCorrectAnswerTimeMs());assertThat(standing.rank()).isEqualTo(state.rank());
        }
        assertThat(jdbc.queryForObject("select count(*) from answer where game_session_id=? and answer_status='NO_ANSWER'",Long.class,m.game())).isEqualTo(10);
        for(int i=11;i<=16;i++)question(m,i,Set.of(ids.get(2)),Set.of());
        assertThat(evidence.read(m.game(),ids)).isEqualTo(first);
        var subset=evidence.read(m.game(),List.of(ids.get(1),ids.get(2)));assertThat(subset.firstStage()).allSatisfy(p->assertThat(p.rank()).isEqualTo(1));
    }
    @Test void nonQuizBoundaryAndMissingOrMismatchedHistoryNeverFabricateEvidence(){
        var m=match(List.of(GameMode.RIDDLE,GameMode.CLUES),List.of(4,6));CommittedGame last=null;
        for(int i=1;i<=4;i++)last=question(m,i,Set.of(m.ids().get(1)),Set.of(m.ids().get(2)));
        var read=evidence.read(m.game(),m.ids());var committed=last;
        assertThat(read.firstStage()).allSatisfy(p->assertThat(p.score()).isEqualTo(committed.players().get(p.userId()).score()));
        long user=m.ids().getFirst();
        jdbc.update("update answer a join player_session p on p.id=a.player_session_id join game_question q on q.id=a.game_question_id set a.result_snapshot=JSON_REMOVE(a.result_snapshot,'$.totalCorrectAnswerTimeMs') where q.game_session_id=? and q.order_index=4 and p.user_id=?",m.game(),user);
        assertThatThrownBy(()->evidence.read(m.game(),m.ids())).hasMessage("AWARD_STAGE_DATA_INCOMPLETE");
    }
}
