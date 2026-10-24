package vn.edu.multigame.room.dto.request;

import jakarta.validation.constraints.*;

public final class ClassMembershipRequests {
    private ClassMembershipRequests() {}
    public record Join(@NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
            @NotNull @Pattern(regexp="[A-Fa-f0-9]{32}") String code) {}
    public record Decision(@NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
            @NotNull @PositiveOrZero Long revision) {}
    public record Batch(@NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId){}
}
