package vn.edu.multigame.questionbank.service;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.questionbank.enums.GameMode;
import static org.assertj.core.api.Assertions.*;
class LearningModeBoundaryTest {
 @Test void authoringModesContainOnlyClassQuestContentTypes(){assertThat(GameMode.values()).extracting(Enum::name).containsExactly("QUIZ","FILL_BLANK","ASSIGNMENT");}
}
