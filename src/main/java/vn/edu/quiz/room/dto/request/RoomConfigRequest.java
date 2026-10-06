package vn.edu.quiz.room.dto.request;

import jakarta.validation.constraints.*;
import vn.edu.quiz.room.enums.Participation;

public record RoomConfigRequest(@NotNull @Positive Long quizId,
        @NotBlank @Size(max = 200) String name, @NotNull @Min(3) @Max(100) Integer maxPlayers,
        @NotNull @Positive Long questionDurationMs, @NotNull Participation hostParticipation) {}
