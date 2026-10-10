package vn.edu.multigame.game.service;
import org.springframework.http.HttpStatus;
public class GameFailure extends RuntimeException {
    private final HttpStatus status;
    public GameFailure(HttpStatus status,String code) { super(code); this.status=status; }
    public HttpStatus status() { return status; }
    public static GameFailure conflict(String code) { return new GameFailure(HttpStatus.CONFLICT,code); }
    public static GameFailure unavailable() { return new GameFailure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE"); }
}
