package vn.edu.multigame.room.dto.request;
import jakarta.validation.constraints.*;
import vn.edu.multigame.game.enums.GameMode;
public record RoomStageRequest(@NotNull GameMode mode,@NotNull @Positive Long quizId,
        @NotNull @Min(1) @Max(50) Integer questionCount,@NotNull @Positive Long questionDurationMs) {}
