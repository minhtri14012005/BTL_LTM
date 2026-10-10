package vn.edu.multigame.game.engine;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.game.enums.GameMode;
import static org.assertj.core.api.Assertions.*;
class ArrangementEvaluatorTest {
 final ArrangementEvaluator engine=new ArrangementEvaluator();
 final List<ArrangementEvaluator.Item> pieces=List.of(new ArrangementEvaluator.Item("a","ha"),new ArrangementEvaluator.Item("space"," "),new ArrangementEvaluator.Item("b","ha"));
 @Test void vietnameseUsesJoinedTextNotIdentityOrderIncludingRepeatedPieces(){
  assertThat(engine.matches(GameMode.VIETNAMESE_PUZZLE,pieces,List.of("a","space","b"),List.of("HA  HA"),List.of("b","space","a"))).isTrue();
  assertThat(engine.matches(GameMode.VIETNAMESE_PUZZLE,pieces,List.of("a","space","b"),List.of("ha ha"),List.of("a","b","space"))).isFalse();
 }
 @Test void orderingUsesExactIdsEvenWhenValuesRepeat(){
  assertThat(engine.matches(GameMode.ORDERING,pieces,List.of("a","space","b"),null,List.of("a","space","b"))).isTrue();
  assertThat(engine.matches(GameMode.ORDERING,pieces,List.of("a","space","b"),null,List.of("b","space","a"))).isFalse();
 }
 @Test void invalidPermutationsAreRejectedForBothModes(){
  for(var mode:List.of(GameMode.ORDERING,GameMode.VIETNAMESE_PUZZLE))for(var ids:List.of(List.of("a","space"),List.of("a","space","b","extra"),List.of("a","a","b"),List.of("a","space","fake")))
   assertThat(engine.matches(mode,pieces,List.of("a","space","b"),List.of("ha ha"),ids)).isFalse();
 }
 @Test void oldV4PayloadWithoutAliasesRemainsPlayable(){assertThat(engine.matches(GameMode.VIETNAMESE_PUZZLE,pieces,List.of("a","space","b"),null,List.of("b","space","a"))).isTrue();}
 @Test void accentsNfcCaseUnicodeSpaceAndPunctuationFollowTextPolicy(){
  var values=List.of(new ArrangementEvaluator.Item("a","VIỆT"),new ArrangementEvaluator.Item("b","\u00a0NAM!"));
  assertThat(engine.matches(GameMode.VIETNAMESE_PUZZLE,values,List.of("a","b"),List.of("việt nam!"),List.of("a","b"))).isTrue();
  assertThat(engine.matches(GameMode.VIETNAMESE_PUZZLE,values,List.of("a","b"),List.of("viet nam!"),List.of("a","b"))).isFalse();
  assertThat(engine.matches(GameMode.VIETNAMESE_PUZZLE,values,List.of("a","b"),List.of("việt nam"),List.of("a","b"))).isFalse();
 }
}
