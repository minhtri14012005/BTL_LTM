package vn.edu.multigame.auth.dto.request;

import jakarta.validation.constraints.*;

public record ProfileRequest(@NotBlank @Size(max=100) String hoTen,
        @NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{0,31}") String maSinhVien) {}
