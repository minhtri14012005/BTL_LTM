package vn.edu.multigame.game.dto;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class MultimodeRulesSnapshotTest {
    @Test void quizCountNotTotalCountAllocatesResourcesAndTiming() throws Exception {
        var rules=MultimodeRulesSnapshot.forGame(50,9);
        assertThat(rules.initialScore()).isZero();assertThat(rules.minimumScore()).isZero();
        assertThat(rules.spinCredits()).isZero();assertThat(rules.starCredits()).isEqualTo(1);
        assertThat(MultimodeRulesSnapshot.forGame(50,20).spinCredits()).isEqualTo(2);
        assertThat(MultimodeRulesSnapshot.forGame(1,0).starCredits()).isZero();
        assertThat(rules.decisionDurationMs()).isEqualTo(7000);assertThat(rules.introDurationMs()).isEqualTo(10000);assertThat(rules.resultDurationMs()).isEqualTo(1500);
        var json=new ObjectMapper();assertThat(json.readValue(json.writeValueAsString(rules),MultimodeRulesSnapshot.class)).isEqualTo(rules);
    }
    @Test void invalidCountsAreRejected() {
        for(int count:List.of(0,51))assertThatThrownBy(()->MultimodeRulesSnapshot.forGame(count,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MultimodeRulesSnapshot.forGame(1,2)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void typedJsonContainsOnlyTheAnswerShape() throws Exception {
        var json=new ObjectMapper();assertThat(json.writeValueAsString(new TypedAnswer("bàn",null))).isEqualTo("{\"text\":\"bàn\"}");
        assertThat(json.writeValueAsString(new TypedAnswer(null,List.of("a","b")))).isEqualTo("{\"itemIds\":[\"a\",\"b\"]}");
        assertThatThrownBy(()->new TypedAnswer("x",List.of("a","b"))).isInstanceOf(IllegalArgumentException.class);
    }
}
