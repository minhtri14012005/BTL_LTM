package vn.edu.multigame.questionbank.dto.request;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.questionbank.enums.TextMatchPolicy;
public record QuestionRequest(@NotBlank @Size(max=5000) String content,
 @Size(min=4,max=4) Map<Option,@NotBlank @Size(max=2000) String> options,
 Option correctAnswer,@Pattern(regexp="sha256:[0-9a-f]{64}") String imageRef,
 @Size(min=1,max=20) List<@NotBlank @Size(max=300) String> acceptedAnswers,TextMatchPolicy matchingPolicy) {
 public QuestionRequest(String content,Map<Option,String> options,Option correctAnswer,String imageRef){this(content,options,correctAnswer,imageRef,null,null);}
}
