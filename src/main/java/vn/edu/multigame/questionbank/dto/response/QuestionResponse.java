package vn.edu.multigame.questionbank.dto.response;
import java.util.Map;
import java.util.List;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.questionbank.enums.TextMatchPolicy;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record QuestionResponse(long id,int position,String content,Map<Option,String> options,Option correctAnswer,String imageRef,List<String> acceptedAnswers,TextMatchPolicy matchingPolicy) {}
