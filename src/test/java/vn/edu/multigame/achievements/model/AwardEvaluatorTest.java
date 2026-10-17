package vn.edu.multigame.achievements.model;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AwardEvaluatorTest {
    final ApprovedAwardPolicy policy=ApprovedAwardPolicy.approved();final AwardEvaluator evaluator=new AwardEvaluator();
    AwardEvaluator.Player player(long id,int score,long time,boolean eligible,int d,int c){return new AwardEvaluator.Player(id,score,time,(int)id,eligible,List.of(new AwardEvaluator.Metric("QUIZ",d,c,time)));}
    AwardEvaluator.Result evaluate(List<AwardEvaluator.Player> ps){return evaluator.evaluate(policy,10,10,true,ps);}
    @Test void originalWinnerRemovedUsesEligibleRankingWithoutChangingInputs(){
        var ps=List.of(player(1,99,20,false,10,10),player(2,80,30,true,10,8),player(3,70,40,true,10,7));var r=evaluate(ps);
        assertThat(r.qualified()).isTrue();assertThat(r.ranks()).containsEntry(2L,1).containsEntry(3L,2).doesNotContainKey(1L);
        assertThat(r.awards()).contains(new AwardEvaluator.Award(2,"CUP","ALL")).doesNotContain(new AwardEvaluator.Award(1,"CUP","ALL"));assertThat(ps.getFirst().historyRank()).isEqualTo(1);
    }
    @Test void removedOnlyCorrectDoesNotQualifyAnyone(){var r=evaluate(List.of(player(1,99,20,false,10,1),player(2,0,0,true,10,0),player(3,0,0,true,10,0)));assertThat(r.reasons()).contains("NO_ELIGIBLE_CORRECT");assertThat(r.awards()).isEmpty();}
    @Test void tiedWinnersEachReceiveOneCupAndCompetitionRanks(){var r=evaluate(List.of(player(1,10,100,true,10,5),player(2,10,100,true,10,5),player(3,9,20,true,10,5)));assertThat(r.ranks()).containsEntry(1L,1).containsEntry(2L,1).containsEntry(3L,3);assertThat(r.awards().stream().filter(a->a.type().equals("CUP"))).hasSize(2);}
    @Test void qualifiedWinnerDoesNotNeedAnAdditionalPersonalCorrectThreshold(){var r=evaluate(List.of(player(1,10,0,true,10,0),player(2,0,50,true,10,1),player(3,0,60,true,10,0)));assertThat(r.awards()).contains(new AwardEvaluator.Award(1,"CUP","ALL"));}
    @Test void minimumsAndNoncompletedGameNeverProduceAwards(){
        var ps=List.of(player(1,10,20,true,10,10),player(2,9,30,true,10,10),player(3,8,40,true,10,10));
        assertThat(evaluator.evaluate(policy,9,9,true,ps).qualified()).isFalse();assertThat(evaluator.evaluate(policy,10,9,true,ps).qualified()).isFalse();assertThat(evaluator.evaluate(policy,10,10,false,ps).qualified()).isFalse();assertThat(evaluate(ps.subList(0,2)).qualified()).isFalse();
    }
    @Test void fewerThanTwoEligibleIsNotQualified(){assertThat(evaluate(List.of(player(1,10,20,true,10,1),player(2,20,30,false,10,5),player(3,30,40,false,10,5))).reasons()).contains("INSUFFICIENT_ELIGIBLE_PLAYERS");}
    @Test void perfectIncludesNoAnswerInDenominatorAndCanHaveMultipleRecipients(){var r=evaluate(List.of(player(1,10,20,true,10,10),player(2,9,30,true,10,10),player(3,8,40,true,10,9)));assertThat(r.awards().stream().filter(a->a.type().equals("PERFECT"))).containsExactlyInAnyOrder(new AwardEvaluator.Award(1,"PERFECT","ALL"),new AwardEvaluator.Award(2,"PERFECT","ALL"));}
    @Test void fastRequiresFiveCorrectAndSixtyPercentWithExactFractionTies(){var r=evaluate(List.of(player(1,10,10,true,10,5),player(2,9,30,true,10,6),player(3,8,40,true,10,8)));assertThat(r.awards().stream().filter(a->a.type().equals("FAST"))).containsExactlyInAnyOrder(new AwardEvaluator.Award(2,"FAST","QUIZ"),new AwardEvaluator.Award(3,"FAST","QUIZ"));}
    @Test void fastIsPerModeAndNeverComparedAcrossDifferentModes(){var a=new AwardEvaluator.Player(1,10,500,1,true,List.of(new AwardEvaluator.Metric("QUIZ",5,5,500),new AwardEvaluator.Metric("SONG",5,5,100)));var b=new AwardEvaluator.Player(2,9,100,2,true,List.of(new AwardEvaluator.Metric("QUIZ",5,5,100),new AwardEvaluator.Metric("SONG",5,5,500)));var c=player(3,0,0,true,10,0);var r=evaluate(List.of(a,b,c));assertThat(r.awards()).contains(new AwardEvaluator.Award(1,"FAST","SONG"),new AwardEvaluator.Award(2,"FAST","QUIZ"));}
    @Test void alteredOrUnknownPolicyIsNeverReinterpretedAsCurrent(){var unknown=new ApprovedAwardPolicy("OTHER",3,10,10,2,1,5,60,10,policy.hash());assertThatThrownBy(()->evaluator.evaluate(unknown,10,10,true,List.of())).hasMessage("AWARD_POLICY_UNSUPPORTED");}
}
