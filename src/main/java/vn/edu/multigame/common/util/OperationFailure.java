package vn.edu.multigame.common.util;
import org.springframework.http.HttpStatus;
public class OperationFailure extends RuntimeException {
    private final HttpStatus status;
    public OperationFailure(HttpStatus status,String code) { super(code); this.status=status; }
    public HttpStatus status() { return status; }
    public static OperationFailure conflict(String code) { return new OperationFailure(HttpStatus.CONFLICT,code); }
    public static OperationFailure unavailable() { return new OperationFailure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE"); }
}
