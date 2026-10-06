package vn.edu.quiz.auth.dto.request;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
        @NotNull @Size(min = 8, max = 72) String password) {
    @Override public String toString() { return "LoginRequest[credentials redacted]"; }
}
