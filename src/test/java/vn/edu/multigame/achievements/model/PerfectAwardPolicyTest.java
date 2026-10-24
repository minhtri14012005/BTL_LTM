package vn.edu.multigame.achievements.model;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PerfectAwardPolicyTest {
 private List<PerfectAwardPolicy.Player> players(int correct,boolean thirdEligible){return List.of(new PerfectAwardPolicy.Player(1,true,correct),new PerfectAwardPolicy.Player(2,true,1),new PerfectAwardPolicy.Player(3,thirdEligible,0));}
 @Test void preservesAllCurrentPerfectAwardThresholds(){
  assertThat(PerfectAwardPolicy.winners(10,10,players(10,false))).containsExactly(1L);
  assertThat(PerfectAwardPolicy.winners(10,9,players(10,true))).isEmpty();
  assertThat(PerfectAwardPolicy.winners(9,9,players(9,true))).isEmpty();
  assertThat(PerfectAwardPolicy.winners(10,10,players(9,true))).isEmpty();
  assertThat(PerfectAwardPolicy.winners(10,10,players(10,true).subList(0,2))).isEmpty();
  assertThat(PerfectAwardPolicy.winners(10,10,List.of(new PerfectAwardPolicy.Player(1,true,10),new PerfectAwardPolicy.Player(2,false,0),new PerfectAwardPolicy.Player(3,false,0)))).isEmpty();
 }
}
