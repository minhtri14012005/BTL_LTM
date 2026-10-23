package vn.edu.multigame.questionbank.service;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.questionbank.dto.response.*;
import vn.edu.multigame.questionbank.enums.TextMatchPolicy;
import vn.edu.multigame.quiz.enums.Option;
import static org.assertj.core.api.Assertions.*;

class StudentQuestionProjectionTest {
    @Test void studentProjectionHasOnlyPromptFieldsAndNoSolutions() throws Exception {
        var secret=new QuestionResponse(91,1,"History {{blank}}",null,null,null,List.of("Secret Đáp Án"),null,null,null,null,null,TextMatchPolicy.EXACT);
        var json=new ObjectMapper().writeValueAsString(StudentQuestion.from(secret));
        assertThat(json).contains("History {{blank}}").doesNotContain("acceptedAnswers","correctAnswer","matchingPolicy","Secret","91");
    }
    @Test void quizOptionsRemainAvailableWithoutMarkingTheCorrectOption() throws Exception {
        var secret=new QuestionResponse(92,2,"Question",Map.of(Option.A,"Alpha",Option.B,"Beta",Option.C,"Gamma",Option.D,"Delta"),Option.C,null,null,null,null,null,null,null);
        var json=new ObjectMapper().writeValueAsString(StudentQuestion.from(secret));
        assertThat(json).contains("Alpha","Beta","Gamma","Delta").doesNotContain("correctAnswer","sourceQuizId");
    }
}
