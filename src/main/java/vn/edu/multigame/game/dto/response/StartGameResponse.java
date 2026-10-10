package vn.edu.multigame.game.dto.response;
import vn.edu.multigame.room.dto.response.RoomResponse;
public record StartGameResponse(long gameSessionId, RoomResponse room, long serverTimeMs) {}
