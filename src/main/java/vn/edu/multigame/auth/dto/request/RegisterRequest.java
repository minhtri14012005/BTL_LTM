package vn.edu.multigame.auth.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
        @NotBlank @Size(max = 100) String hoTen,
        @NotNull @Size(min = 8, max = 72) String password,
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,31}") String maSinhVien) {
    @Override public String toString() { return "RegisterRequest[credentials redacted]"; }
}
