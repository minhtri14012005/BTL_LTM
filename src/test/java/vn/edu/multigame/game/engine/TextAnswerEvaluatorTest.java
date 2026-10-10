package vn.edu.multigame.game.engine;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TextAnswerEvaluatorTest {
    final TextAnswerEvaluator evaluator=new TextAnswerEvaluator();
    @Test void preservesVietnameseAccentsAndMatchesAliases() {
        assertThat(evaluator.matches(" CÁI   BÀN ",List.of("cái bàn","bàn"))).isTrue();
        assertThat(evaluator.matches("bàn",List.of("cái bàn","bàn"))).isTrue();
        assertThat(evaluator.matches("cai ban",List.of("cái bàn"))).isFalse();
        assertThat(evaluator.matches("cái bàng",List.of("cái bàn"))).isFalse();
    }
    @Test void unicodeNfcAndUnicodeWhitespaceAreEquivalent() {
        assertThat(evaluator.matches("  CA\u0301I\u00a0\tBA\u0300N\n",List.of("cái bàn"))).isTrue();
        assertThat(evaluator.normalize(" \t\u00a0")).isEmpty();
    }
    @Test void noFuzzyPrefixOrPunctuationRemoval() {
        assertThat(evaluator.matches("bàn!",List.of("bàn"))).isFalse();
        assertThat(evaluator.matches("bà",List.of("bàn"))).isFalse();
        assertThat(evaluator.matches(" ",List.of("bàn"))).isFalse();
        assertThatThrownBy(()->evaluator.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
