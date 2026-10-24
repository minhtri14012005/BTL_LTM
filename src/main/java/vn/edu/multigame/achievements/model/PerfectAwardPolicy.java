package vn.edu.multigame.achievements.model;
import java.util.List;
public final class PerfectAwardPolicy {
 private PerfectAwardPolicy(){}
 public record Player(long userId,boolean eligible,int correct){}
 public static List<Long> winners(int configured,int opened,List<Player> initialPlayers){
  var eligible=initialPlayers.stream().filter(Player::eligible).toList();
  if(initialPlayers.size()<3 || configured<10 || opened<10 || eligible.size()<2 || eligible.stream().mapToInt(Player::correct).sum()<1)return List.of();
  return eligible.stream().filter(p->p.correct()==configured).map(Player::userId).toList();
 }
}
