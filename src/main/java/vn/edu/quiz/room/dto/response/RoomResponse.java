package vn.edu.quiz.room.dto.response;

import java.util.List;
import vn.edu.quiz.room.enums.*;

/** Shared, question-free committed Room snapshot. No Entity is serialized. */
public record RoomResponse(long id, long hostUserId, long quizId, String quizTitle, boolean quizDeleted,
        String roomCode, String name, RoomStatus status, int maxPlayers, long questionDurationMs,
        long decisionDurationMs, long createdAtMs, long revision, List<Member> members) {
    public record Member(long membershipId, long userId, String displayName, boolean host,
            Participation participation, long joinedAtMs) {}
}
