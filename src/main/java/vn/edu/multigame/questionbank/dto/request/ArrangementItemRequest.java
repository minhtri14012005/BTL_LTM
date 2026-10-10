package vn.edu.multigame.questionbank.dto.request;
import jakarta.validation.constraints.*;
/** IDs remain unchanged when editing a label; a Vietnamese piece can contain spaces. */
public record ArrangementItemRequest(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String id,
        @NotNull @Size(min=1,max=300) String text) {}
