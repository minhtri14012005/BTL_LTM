package vn.edu.multigame.game.dto.request;
import jakarta.validation.constraints.*;
public record StartGameRequest(
        @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
        @NotNull @PositiveOrZero Long revision, @NotNull @Min(1) @Max(50) Integer questionCount) {}
