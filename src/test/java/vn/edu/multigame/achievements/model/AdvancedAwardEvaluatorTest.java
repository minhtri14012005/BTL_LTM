package vn.edu.multigame.achievements.model;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AdvancedAwardEvaluatorTest {
    final ApprovedAwardPolicy policy=ApprovedAwardPolicy.active();
    final AdvancedAwardEvaluator advanced=new AdvancedAwardEvaluator();
    AwardEvaluator.Player player(long id,int score,boolean eligible,int d,int c,int modes){
        var metrics=new ArrayList<AwardEvaluator.Metric>();for(int i=0;i<modes;i++)metrics.add(new AwardEvaluator.Metric(List.of("QUIZ","RIDDLE","CLUES").get(i),d,c,100L*c));
        return new AwardEvaluator.Player(id,score,100L*c*modes,(int)id,eligible,List.copyOf(metrics));
    }
    AwardEvaluator.Result base(List<AwardEvaluator.Player> p){return new AwardEvaluator().evaluate(policy,12,12,true,p);}
    StageRankEvidence single(){return new StageRankEvidence(1,0,List.of());}
    List<AwardEvaluator.Award> evaluate(List<AwardEvaluator.Player> p,StageRankEvidence stage){return advanced.evaluate(policy,base(p),p,stage);}
    @Test void milestoneGoesToEveryQualifiedPlayerIncludingNonWinner(){
        var p=List.of(player(1,100,true,4,2,3),player(2,50,true,4,2,3),player(3,1,true,4,1,3));
        assertThat(evaluate(p,single())).extracting(AwardEvaluator.Award::userId).containsExactly(1L,2L);
    }
    @Test void exactFiftyPercentAndMinimumCorrectAndModeCount(){
        var p=List.of(player(1,100,true,5,2,3),player(2,50,true,4,2,2),player(3,1,true,2,1,3));assertThat(evaluate(p,single())).isEmpty();
        var exact=List.of(player(1,100,true,4,2,3),player(2,50,true,4,0,3),player(3,1,true,4,0,3));assertThat(evaluate(exact,single())).hasSize(1);
    }
    @Test void duplicateModesAreNotThreeDifferentModes(){
        var p=new AwardEvaluator.Player(1,100,600,1,true,Collections.nCopies(3,new AwardEvaluator.Metric("QUIZ",4,2,200)));
        assertThat(evaluate(List.of(p,player(2,1,true,4,1,3),player(3,0,true,4,1,3)),single())).isEmpty();
    }
    @Test void ineligibleAndUnqualifiedNeverReceiveAdvancedAwards(){
        var p=List.of(player(1,100,false,4,4,3),player(2,50,true,4,1,3),player(3,1,true,4,1,3));assertThat(evaluate(p,single())).isEmpty();
        var no=new AwardEvaluator().evaluate(policy,9,9,true,p);assertThat(advanced.evaluate(policy,no,p,null)).isEmpty();
    }
    @Test void uniqueLastToUniqueFirstReceivesComeback(){
        var p=List.of(player(1,100,true,4,1,3),player(2,50,true,4,1,3),player(3,1,true,4,1,3));
        var stage=new StageRankEvidence(3,4,List.of(new StageRankEvidence.Standing(1,0,0,3),new StageRankEvidence.Standing(2,20,200,1),new StageRankEvidence.Standing(3,10,100,2)));
        var result=evaluate(p,stage);assertThat(result).hasSize(1);assertThat(result.getFirst().type()).isEqualTo("COMEBACK");assertThat(result.getFirst().evidence()).containsEntry("stageOneEndQuestion",4).containsEntry("finalEligibleRank",1);
    }
    @Test void tiedLastOrTiedFirstNeverReceivesComeback(){
        var p=List.of(player(1,100,true,4,1,3),player(2,50,true,4,1,3),player(3,1,true,4,1,3));
        var tie=new StageRankEvidence(3,4,List.of(new StageRankEvidence.Standing(1,0,0,2),new StageRankEvidence.Standing(2,20,100,1),new StageRankEvidence.Standing(3,0,0,2)));assertThat(evaluate(p,tie)).isEmpty();
        var finalTie=List.of(player(1,100,true,4,1,3),player(2,100,true,4,1,3),player(3,1,true,4,1,3));
        var stage=new StageRankEvidence(3,4,List.of(new StageRankEvidence.Standing(1,0,0,3),new StageRankEvidence.Standing(2,20,200,1),new StageRankEvidence.Standing(3,10,100,2)));assertThat(evaluate(finalTie,stage)).isEmpty();
    }
    @Test void missingStageOrWrongEligibleSetFailsClosed(){
        var p=List.of(player(1,100,true,4,1,3),player(2,50,true,4,1,3),player(3,1,true,4,1,3));
        assertThatThrownBy(()->evaluate(p,null)).hasMessage("AWARD_STAGE_DATA_INCOMPLETE");
        assertThatThrownBy(()->evaluate(p,new StageRankEvidence(2,4,List.of(new StageRankEvidence.Standing(1,0,0,1))))).hasMessage("AWARD_STAGE_DATA_INCOMPLETE");
    }
    @Test void legacySnapshotKeepsOldHashAndNeverReceivesAdvancedAwards()throws Exception{
        var json=new ObjectMapper();var v1=ApprovedAwardPolicy.approved();String stored=json.writeValueAsString(v1);assertThat(stored).doesNotContain("advanced");
        var read=json.readValue(stored,ApprovedAwardPolicy.class);read.requireSupported();assertThat(read).isEqualTo(v1);assertThat(read.advanced()).isNull();
        var p=List.of(player(1,100,true,4,4,3),player(2,50,true,4,4,3),player(3,1,true,4,4,3));
        assertThat(advanced.evaluate(read,new AwardEvaluator().evaluate(read,12,12,true,p),p,null)).isEmpty();assertThat(policy.hash()).isNotEqualTo(v1.hash());
    }
    @Test void unsupportedAdvancedThresholdCannotReuseSignedPolicyIdAndHash(){
        var bad=new ApprovedAwardPolicy(policy.policyId(),3,10,10,2,1,5,60,10,policy.hash(),new ApprovedAwardPolicy.Advanced(2,1,10,1));
        assertThatThrownBy(bad::requireSupported).hasMessage("AWARD_POLICY_UNSUPPORTED");
    }
}
