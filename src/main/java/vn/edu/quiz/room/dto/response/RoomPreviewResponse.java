package vn.edu.quiz.room.dto.response;
import vn.edu.quiz.room.enums.RoomStatus;
/** By-code lookup for Join, deliberately excludes roster and Quiz contents. */
public record RoomPreviewResponse(long id, String roomCode, String name, RoomStatus status, int maxPlayers) {}
