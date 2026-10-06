package vn.edu.quiz.room.service;
import org.springframework.http.HttpStatus;
public class RoomFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public RoomFailure(HttpStatus status, String code) { super(code); this.status = status; this.code = code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public boolean retryable() { return status == HttpStatus.SERVICE_UNAVAILABLE && !code.equals("ROOM_UNAVAILABLE"); }
    public static RoomFailure invalid() { return new RoomFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"); }
    public static RoomFailure missing() { return new RoomFailure(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"); }
    public static RoomFailure forbidden() { return new RoomFailure(HttpStatus.FORBIDDEN, "FORBIDDEN"); }
    public static RoomFailure conflict(String code) { return new RoomFailure(HttpStatus.CONFLICT, code); }
}
