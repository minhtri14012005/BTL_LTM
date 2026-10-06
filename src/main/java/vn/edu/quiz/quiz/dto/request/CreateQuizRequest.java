package vn.edu.quiz.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import vn.edu.quiz.quiz.enums.Visibility;

public record CreateQuizRequest(@NotBlank @Size(max = 200) String title,
        @NotNull Visibility visibility,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid QuestionRequest> questions) {}
