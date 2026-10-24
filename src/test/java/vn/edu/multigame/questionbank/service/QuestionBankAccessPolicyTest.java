package vn.edu.multigame.questionbank.service;

import org.junit.jupiter.api.Test;
import vn.edu.multigame.questionbank.enums.Visibility;
import static org.assertj.core.api.Assertions.*;

class QuestionBankAccessPolicyTest {
    QuestionBankAccessPolicy policy = new QuestionBankAccessPolicy();
    @Test void privateSelectionIsOwnerOnlyAndPublicCanBeUsedByOthers() {
        policy.requireUse(1, 1, Visibility.PRIVATE); policy.requireUse(2, 1, Visibility.PUBLIC);
        assertThatThrownBy(() -> policy.requireUse(2, 1, Visibility.PRIVATE)).isInstanceOfSatisfying(QuestionBankFailure.class,
                error -> assertThat(error.status().value()).isEqualTo(404));
        assertThatThrownBy(() -> policy.requireOwner(2, 1)).isInstanceOf(QuestionBankFailure.class);
    }
}
