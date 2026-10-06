package vn.edu.quiz.quiz.service;

import org.junit.jupiter.api.Test;
import vn.edu.quiz.quiz.enums.Visibility;
import vn.edu.quiz.room.enums.Participation;
import static org.assertj.core.api.Assertions.*;

class QuizAccessPolicyTest {
    QuizAccessPolicy policy = new QuizAccessPolicy();
    @Test void privateSelectionIsOwnerOnlyAndPublicCanBeUsedByOthers() {
        policy.requireUse(1, 1, Visibility.PRIVATE); policy.requireUse(2, 1, Visibility.PUBLIC);
        assertThatThrownBy(() -> policy.requireUse(2, 1, Visibility.PRIVATE)).isInstanceOfSatisfying(QuizFailure.class,
                error -> assertThat(error.status().value()).isEqualTo(404));
        assertThatThrownBy(() -> policy.requireOwner(2, 1)).isInstanceOf(QuizFailure.class);
    }
    @Test void authorMayHostSpectateButNeverParticipateAsPlayer() {
        policy.requireParticipation(1, 1, Participation.SPECTATOR);
        policy.requireParticipation(2, 1, Participation.PLAYER);
        assertThatThrownBy(() -> policy.requireParticipation(1, 1, Participation.PLAYER)).isInstanceOfSatisfying(QuizFailure.class,
                error -> assertThat(error.code()).isEqualTo("QUIZ_AUTHOR_CANNOT_PLAY"));
    }
    @Test void entitySerializerDoesNotExposeCorrectOption() throws Exception {
        var question = new vn.edu.quiz.quiz.entity.Question();
        question.setCorrectOption(vn.edu.quiz.quiz.enums.Option.B);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(question)).doesNotContain("correctOption", "correctAnswer");
    }
}
