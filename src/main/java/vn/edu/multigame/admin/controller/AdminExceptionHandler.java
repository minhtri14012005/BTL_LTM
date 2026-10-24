package vn.edu.multigame.admin.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.multigame.auth.dto.response.AuthError;
import vn.edu.multigame.auth.service.AuthFailure;

@RestControllerAdvice(basePackages="vn.edu.multigame.admin.controller") @Profile("mysql")
public class AdminExceptionHandler {
    @ExceptionHandler(AuthFailure.class) ResponseEntity<AuthError> auth(AuthFailure e){return error(e.status(),e.code(),e.getMessage());}
    @ExceptionHandler(vn.edu.multigame.room.service.RoomFailure.class) ResponseEntity<AuthError> room(vn.edu.multigame.room.service.RoomFailure e){return error(e.status(),e.code(),e.getMessage());}
    @ExceptionHandler(vn.edu.multigame.common.util.OperationFailure.class) ResponseEntity<AuthError> game(vn.edu.multigame.common.util.OperationFailure e){return error(e.status(),e.getMessage(),e.getMessage());}
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<AuthError> body(Exception e){return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Dữ liệu quản trị không hợp lệ.");}
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AuthError> unavailable(DataAccessException e) {
        for (Throwable cause=e; cause!=null; cause=cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && sql.getErrorCode()==1062)
                return error(HttpStatus.CONFLICT,"IDENTITY_TAKEN","Tên đăng nhập hoặc mã sinh viên đã tồn tại.");
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE,"ADMIN_UNAVAILABLE","Chưa thể đọc dữ liệu quản trị.");
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class) ResponseEntity<AuthError> invalid(Exception e){return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Tham số không hợp lệ.");}
    private ResponseEntity<AuthError> error(HttpStatus status,String code,String message){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new AuthError(code,message,System.currentTimeMillis()));}
}
