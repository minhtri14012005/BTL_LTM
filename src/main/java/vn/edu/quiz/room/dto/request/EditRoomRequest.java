package vn.edu.quiz.room.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record EditRoomRequest(@NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
        @NotNull @PositiveOrZero Long revision, @NotNull @Valid RoomConfigRequest config) {}
