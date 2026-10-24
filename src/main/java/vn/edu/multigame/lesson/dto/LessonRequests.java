package vn.edu.multigame.lesson.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.edu.multigame.lesson.domain.LessonMode;

public final class LessonRequests {
    private LessonRequests() {}
    public record Slot(@NotNull LessonMode mode,@NotBlank @Size(max=200) String title,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal budget) {}
    public record CopySettings(@NotNull @Min(1) @Max(3) Integer position,Integer durationMs,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=vn.edu.multigame.lesson.service.GameSettingsService.QuestionCountDeserializer.class) Integer questionCount,List<String> wheelCells,Integer selectedCount,Long assignmentDeadlineAtMs) {}
    public record Plan(@NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
            @NotBlank @Size(max=200) String title,@NotNull @Size(min=3,max=3) List<@NotNull @Valid Slot> slots, @Positive Long sourceLessonId, @Size(max=3) List<@NotNull @Valid CopySettings> gameSettings) {
        public Plan(String requestId,String title,List<Slot> slots){this(requestId,title,slots,null,null);}
    }
    public record Edit(@NotNull @PositiveOrZero Long revision,@NotNull @Valid Plan plan) {}
    public record Visibility(@NotNull @PositiveOrZero Long revision,@NotNull Boolean visible) {}
    public record Transition(@NotNull @PositiveOrZero Long revision) {}
}
