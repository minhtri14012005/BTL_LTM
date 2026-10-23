package vn.edu.multigame.questionbank.service;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.multigame.game.enums.GameMode;
import static org.assertj.core.api.Assertions.*;
class LearningModeBoundaryTest {
 @Test void retiredModesCannotEnterQuestionAuthoring(){
  var service=new QuestionBankService(null,null,null,null,null,null,null,null,null);
  var oldText=new vn.edu.multigame.questionbank.dto.request.QuestionRequest("Câu đố cũ",null,null,null,List.of("Đáp án"));
  for(var mode:GameMode.values())if(!QuestionBankService.learningMode(mode))assertThatThrownBy(()->service.validateQuestions(mode,List.of(oldText))).isInstanceOf(QuestionBankFailure.class);
 }
}
