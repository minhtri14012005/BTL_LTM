package vn.edu.quiz.auth.controller;

import java.sql.SQLException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.edu.quiz.auth.dto.response.AuthError;
import vn.edu.quiz.auth.service.AuthFailure;

@RestControllerAdvice(basePackages = "vn.edu.quiz.auth.controller")
@Profile("mysql")
public class AuthExceptionHandler {
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<AuthError> failure(AuthFailure e) { return error(e.status(), e.code(), e.getMessage()); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<AuthError> credentials(AuthenticationException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không hợp lệ.");
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<AuthError> invalid(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu đăng nhập/đăng ký không hợp lệ.");
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AuthError> database(DataAccessException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return error(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Tên đăng nhập đã tồn tại.");
            }
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "Chưa thể xác thực; thử lại sau.");
    }
    private ResponseEntity<AuthError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new AuthError(code, message, System.currentTimeMillis()));
    }
}
