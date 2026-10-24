package vn.edu.multigame.questionbank.service;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.questionbank.dto.response.*;
import vn.edu.multigame.questionbank.enums.TextMatchPolicy;
import vn.edu.multigame.quiz.enums.Option;
import static org.assertj.core.api.Assertions.*;

class StudentQuestionProjectionTest {
    @Test void readsExistingLessonSnapshotsWithoutRewritingTheirHistoricalJson() throws Exception {
        var question=new ObjectMapper().readValue("{\"id\":12,\"position\":1,\"content\":\"Existing question\",\"options\":{\"A\":\"One\",\"B\":\"Two\",\"C\":\"Three\",\"D\":\"Four\"},\"correctAnswer\":\"B\",\"hints\":null,\"mediaRef\":null,\"arrangementItems\":null,\"correctOrder\":null}",QuestionResponse.class);
        assertThat(question.id()).isEqualTo(12);
        assertThat(question.correctAnswer()).isEqualTo(Option.B);
        assertThat(question.options()).hasSize(4);
    }
    @Test void studentProjectionHasOnlyPromptFieldsAndNoSolutions() throws Exception {
        var secret=new QuestionResponse(91,1,"History {{blank}}",null,null,null,List.of("Secret Đáp Án"),TextMatchPolicy.EXACT);
        var json=new ObjectMapper().writeValueAsString(StudentQuestion.from(secret));
        assertThat(json).contains("History {{blank}}").doesNotContain("acceptedAnswers","correctAnswer","matchingPolicy","Secret","91");
    }
    @Test void quizOptionsRemainAvailableWithoutMarkingTheCorrectOption() throws Exception {
        var secret=new QuestionResponse(92,2,"Question",Map.of(Option.A,"Alpha",Option.B,"Beta",Option.C,"Gamma",Option.D,"Delta"),Option.C,null,null,null);
        var json=new ObjectMapper().writeValueAsString(StudentQuestion.from(secret));
        assertThat(json).contains("Alpha","Beta","Gamma","Delta").doesNotContain("correctAnswer","sourceQuizId");
    }
}
