package vn.edu.multigame.room.dto.request;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.edu.multigame.room.enums.Participation;
/** Either legacy quiz/duration or a stage list; cross-field invariants are checked in RoomService. */
public record RoomConfigRequest(@Positive Long quizId,
        @NotBlank @Size(max=200) String name,@NotNull @Min(3) @Max(100) Integer maxPlayers,
        @Positive Long questionDurationMs,@NotNull Participation hostParticipation,
        @Size(min=1,max=7) List<@NotNull @Valid RoomStageRequest> stages) {
    public RoomConfigRequest(Long quizId,String name,Integer maxPlayers,Long questionDurationMs,Participation hostParticipation) {
        this(quizId,name,maxPlayers,questionDurationMs,hostParticipation,null);
    }
}
