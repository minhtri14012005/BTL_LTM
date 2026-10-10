package vn.edu.multigame.room.dto.response;
public record RoomError(String code, String message, boolean retryable, long serverTimeMs) {}
