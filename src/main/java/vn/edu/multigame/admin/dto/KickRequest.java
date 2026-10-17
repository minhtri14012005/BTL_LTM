package vn.edu.multigame.admin.dto;
import jakarta.validation.constraints.*;
public record KickRequest(@NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
        @Positive long userId,@NotBlank @Size(max=240) String reason) {}
