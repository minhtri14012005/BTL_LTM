package vn.edu.multigame.questionbank.dto.request;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import vn.edu.multigame.quiz.enums.Option;
public record QuestionRequest(@NotBlank @Size(max=5000) String content,
        @Size(min=4,max=4) Map<Option,@NotBlank @Size(max=2000) String> options,
        Option correctAnswer, @Pattern(regexp="sha256:[0-9a-f]{64}") String imageRef,
        @Size(min=1,max=20) List<@NotBlank @Size(max=300) String> acceptedAnswers,
        @Size(min=2,max=100) List<@jakarta.validation.Valid @NotNull ArrangementItemRequest> pieces,
        @Size(min=2,max=100) List<@jakarta.validation.Valid @NotNull ArrangementItemRequest> items,
        @Size(min=2,max=100) List<@NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String> correctOrder, @Pattern(regexp="sha256:[0-9a-f]{64}") String mediaRef, @Size(min=1,max=20) List<@jakarta.validation.Valid @NotNull HintRequest> hints) {
    public QuestionRequest(String content,Map<Option,String> options,Option correctAnswer,String imageRef,List<String> acceptedAnswers,List<ArrangementItemRequest> pieces,List<ArrangementItemRequest> items,List<String> correctOrder,String mediaRef) {
        this(content,options,correctAnswer,imageRef,acceptedAnswers,pieces,items,correctOrder,mediaRef,null);
    }
    public QuestionRequest(String content,Map<Option,String> options,Option correctAnswer,String imageRef,List<String> acceptedAnswers,List<ArrangementItemRequest> pieces,List<ArrangementItemRequest> items,List<String> correctOrder) {
        this(content,options,correctAnswer,imageRef,acceptedAnswers,pieces,items,correctOrder,null);
    }
    public QuestionRequest(String content,Map<Option,String> options,Option correctAnswer,String imageRef,List<String> acceptedAnswers) {
        this(content,options,correctAnswer,imageRef,acceptedAnswers,null,null,null);
    }
    public QuestionRequest(String content,Map<Option,String> options,Option correctAnswer,String imageRef) {
        this(content,options,correctAnswer,imageRef,null);
    }
}
