package vn.edu.quiz.room.dto.response;
public record RoomError(String code, String message, boolean retryable, long serverTimeMs) {}
