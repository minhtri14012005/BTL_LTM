package vn.edu.multigame.admin.dto;
import jakarta.validation.constraints.*;
public final class TeacherChanges {
 private TeacherChanges(){}
 public record Edit(@NotNull @PositiveOrZero Long expectedVersion,
  @NotNull @Pattern(regexp="[A-Za-z0-9_]{3,32}") String username,@NotBlank @Size(max=100) String hoTen){}
 public record Password(@NotNull @PositiveOrZero Long expectedVersion,@NotNull @Size(min=8,max=72) String password){
  @Override public String toString(){return "Password[redacted]";}
 }
 public record Delete(@NotNull @PositiveOrZero Long expectedVersion){}
}
