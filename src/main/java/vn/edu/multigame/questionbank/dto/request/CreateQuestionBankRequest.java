package vn.edu.multigame.questionbank.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.game.enums.GameMode;

public record CreateQuestionBankRequest(@NotBlank @Size(max = 200) String title,
        @NotNull Visibility visibility,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid QuestionRequest> questions, GameMode mode) {
    public CreateQuestionBankRequest(String title, Visibility visibility, List<QuestionRequest> questions) {
        this(title,visibility,questions,null);
    }
}
