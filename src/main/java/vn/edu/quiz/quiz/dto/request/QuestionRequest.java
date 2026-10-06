package vn.edu.quiz.quiz.dto.request;

import jakarta.validation.constraints.*;
import java.util.Map;
import vn.edu.quiz.quiz.enums.Option;

public record QuestionRequest(
        @NotBlank @Size(max = 5000) String content,
        @NotNull @Size(min = 4, max = 4) Map<Option, @NotBlank @Size(max = 2000) String> options,
        @NotNull Option correctAnswer,
        @Pattern(regexp = "sha256:[0-9a-f]{64}") String imageRef) {}
