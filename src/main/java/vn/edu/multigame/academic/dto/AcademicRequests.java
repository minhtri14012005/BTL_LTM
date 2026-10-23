package vn.edu.multigame.academic.dto;

import jakarta.validation.constraints.*;

public final class AcademicRequests {
    private AcademicRequests() {}
    public record Year(@NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{0,39}") String code,
            @NotBlank @Size(max=120) String name) {}
    public record Semester(@NotNull @Positive Long academicYearId,
            @NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{0,39}") String code,
            @NotBlank @Size(max=120) String name) {}
    public record Subject(@NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{0,39}") String code,
            @NotBlank @Size(max=200) String name) {}
    public record Offering(@NotNull @Positive Long semesterId,@NotNull @Positive Long subjectId) {}
    public record CourseClass(@NotNull @Positive Long offeringId,
            @NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{0,39}") String code,
            @NotBlank @Size(max=120) String name,@NotNull @Positive Integer requiredLessons) {}
}
