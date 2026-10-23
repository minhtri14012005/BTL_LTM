package vn.edu.multigame.admin.dto;

import jakarta.validation.constraints.*;

public record TeacherRequest(@NotNull @Pattern(regexp="[A-Za-z0-9_]{3,32}") String username,
        @NotBlank @Size(max=100) String hoTen,
        @NotNull @Size(min=8,max=72) String password) {
    @Override public String toString() { return "TeacherRequest[credentials redacted]"; }
}
