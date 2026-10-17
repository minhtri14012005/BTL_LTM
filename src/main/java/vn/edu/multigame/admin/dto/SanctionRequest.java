package vn.edu.multigame.admin.dto;

import jakarta.validation.constraints.*;
public record SanctionRequest(
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
    @NotNull @Min(0) Long expectedVersion,
    @Min(1) @Max(525600) Integer minutes,
    @NotBlank @Size(max=240) String reason) {}
