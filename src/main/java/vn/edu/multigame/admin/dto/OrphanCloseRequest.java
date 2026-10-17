package vn.edu.multigame.admin.dto;
import jakarta.validation.constraints.*;
public record OrphanCloseRequest(
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
    @NotNull @Min(0) Long revision,@NotBlank @Size(max=240) String reason) {}
