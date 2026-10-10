package vn.edu.multigame.room.dto.response;
import java.util.List;
import vn.edu.multigame.room.enums.*;
import vn.edu.multigame.game.enums.GameMode;
/** Shared question-free committed Room snapshot, including selected PRIVATE metadata for joined invitees. */
public record RoomResponse(long id,long hostUserId,Long quizId,String quizTitle,boolean quizDeleted,
        String roomCode,String name,RoomStatus status,int maxPlayers,Long questionDurationMs,
        long decisionDurationMs,long createdAtMs,long revision,List<Member> members,int configVersion,List<Stage> stages) {
    public RoomResponse(long id,long hostUserId,long quizId,String quizTitle,boolean quizDeleted,
            String roomCode,String name,RoomStatus status,int maxPlayers,long questionDurationMs,
            long decisionDurationMs,long createdAtMs,long revision,List<Member> members) {
        this(id,hostUserId,quizId,quizTitle,quizDeleted,roomCode,name,status,maxPlayers,questionDurationMs,
                decisionDurationMs,createdAtMs,revision,members,1,List.of());
    }
    public record Member(long membershipId,long userId,String displayName,boolean host,
            Participation participation,long joinedAtMs) {}
    public record Stage(int orderIndex,GameMode mode,long quizId,long ownerUserId,String title,boolean deleted,
            int questionCount,long questionDurationMs) {}
}
