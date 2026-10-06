package vn.edu.quiz.room.service;
import vn.edu.quiz.room.dto.response.RoomResponse;
public record RoomMutation(RoomResponse snapshot, boolean changed, Long gameSessionId) {
    public RoomMutation(RoomResponse snapshot, boolean changed) { this(snapshot, changed, null); }
}
