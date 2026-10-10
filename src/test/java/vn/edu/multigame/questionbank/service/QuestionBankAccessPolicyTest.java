package vn.edu.multigame.questionbank.service;

import org.junit.jupiter.api.Test;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.room.enums.Participation;
import static org.assertj.core.api.Assertions.*;

class QuestionBankAccessPolicyTest {
    QuestionBankAccessPolicy policy = new QuestionBankAccessPolicy();
    @Test void privateSelectionIsOwnerOnlyAndPublicCanBeUsedByOthers() {
        policy.requireUse(1, 1, Visibility.PRIVATE); policy.requireUse(2, 1, Visibility.PUBLIC);
        assertThatThrownBy(() -> policy.requireUse(2, 1, Visibility.PRIVATE)).isInstanceOfSatisfying(QuestionBankFailure.class,
                error -> assertThat(error.status().value()).isEqualTo(404));
        assertThatThrownBy(() -> policy.requireOwner(2, 1)).isInstanceOf(QuestionBankFailure.class);
    }
    @Test void authorMayHostSpectateButNeverParticipateAsPlayer() {
        policy.requireParticipation(1, 1, Participation.SPECTATOR);
        policy.requireParticipation(2, 1, Participation.PLAYER);
        assertThatThrownBy(() -> policy.requireParticipation(1, 1, Participation.PLAYER)).isInstanceOfSatisfying(QuestionBankFailure.class,
                error -> assertThat(error.code()).isEqualTo("QUIZ_AUTHOR_CANNOT_PLAY"));
    }
    @Test void entitySerializerDoesNotExposeCorrectOption() throws Exception {
        var question = new vn.edu.multigame.questionbank.entity.Question();
        question.setCorrectOption(vn.edu.multigame.quiz.enums.Option.B);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(question)).doesNotContain("correctOption", "correctAnswer");
    }
}
