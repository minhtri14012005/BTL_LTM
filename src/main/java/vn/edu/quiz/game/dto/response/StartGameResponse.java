package vn.edu.quiz.game.dto.response;
import vn.edu.quiz.room.dto.response.RoomResponse;
public record StartGameResponse(long gameSessionId, RoomResponse room, long serverTimeMs) {}
