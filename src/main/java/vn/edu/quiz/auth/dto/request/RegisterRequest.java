package vn.edu.quiz.auth.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull @Size(min = 8, max = 72) String password) {
    @Override public String toString() { return "RegisterRequest[credentials redacted]"; }
}
