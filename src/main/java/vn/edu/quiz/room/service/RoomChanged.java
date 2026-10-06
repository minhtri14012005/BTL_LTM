package vn.edu.quiz.room.service;
import vn.edu.quiz.room.dto.response.RoomResponse;
/** Published only after the transaction has committed, while the Room boundary is held. */
public record RoomChanged(RoomResponse snapshot) {}
