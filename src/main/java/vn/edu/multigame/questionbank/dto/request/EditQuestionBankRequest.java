package vn.edu.multigame.questionbank.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.game.enums.GameMode;

public record EditQuestionBankRequest(@NotBlank @Size(max = 200) String title,
        @NotNull Visibility visibility, @NotNull @Min(0) Long revision,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid QuestionRequest> questions, GameMode mode) {
    public EditQuestionBankRequest(String title, Visibility visibility, Long revision, List<QuestionRequest> questions) {
        this(title,visibility,revision,questions,null);
    }
}
