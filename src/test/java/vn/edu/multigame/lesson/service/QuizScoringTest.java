package vn.edu.multigame.lesson.service;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class QuizScoringTest {
 @Test void roundsTheWholeProportionWithoutSpeedOrFinalQuestionBonuses(){
  assertThat(QuizScoring.score(1,7,new BigDecimal("40"))).isEqualByComparingTo("5.71");
  assertThat(QuizScoring.score(7,7,new BigDecimal("40"))).isEqualByComparingTo("40.00");
  assertThat(QuizScoring.score(6,7,new BigDecimal("40"))).isEqualByComparingTo("34.29");
  assertThat(QuizScoring.score(0,7,new BigDecimal("40"))).isEqualByComparingTo("0.00");
  assertThat(QuizScoring.score(39,40,new BigDecimal("40"))).isEqualByComparingTo("39.00");
  assertThat(QuizScoring.score(40,40,new BigDecimal("40"))).isEqualByComparingTo("40.00");
  assertThat(QuizScoring.score(1,40,new BigDecimal("7"))).isEqualByComparingTo("0.18");
  assertThat(QuizScoring.score(40,40,new BigDecimal("7"))).isEqualByComparingTo("7.00");
  assertThatThrownBy(()->QuizScoring.score(0,0,BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
 }
}
