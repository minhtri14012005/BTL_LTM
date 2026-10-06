package vn.edu.quiz.room.dto.response;
import java.util.List;
public record RoomListResponse(List<RoomResponse> items, int page, int size, long totalElements) {}
