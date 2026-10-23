package vn.edu.multigame.lesson.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.edu.multigame.questionbank.dto.request.QuestionRequest;

public final class GameContentRequests {
    private GameContentRequests() {}
    public record Select(@NotNull @Positive Long quizId,@NotNull @PositiveOrZero Long lessonRevision) {}
    public record Edit(@NotNull @PositiveOrZero Long revision,@NotBlank @Size(max=200) String title,
            @NotNull @Size(min=1,max=50) List<@NotNull @Valid QuestionRequest> questions) {}
}
