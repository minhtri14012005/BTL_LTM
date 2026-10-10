package vn.edu.multigame.room.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record CreateRoomRequest(@NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
        @NotNull @Valid RoomConfigRequest config) {}
