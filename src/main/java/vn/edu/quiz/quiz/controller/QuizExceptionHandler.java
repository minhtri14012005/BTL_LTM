package vn.edu.quiz.quiz.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import vn.edu.quiz.auth.service.AuthFailure;
import vn.edu.quiz.quiz.dto.response.QuizError;
import vn.edu.quiz.quiz.service.QuizFailure;

@RestControllerAdvice(basePackages = "vn.edu.quiz.quiz.controller")
@Profile("mysql")
public class QuizExceptionHandler {
    @ExceptionHandler(QuizFailure.class)
    ResponseEntity<QuizError> failure(QuizFailure e) { return error(e.status(), e.code(), e.getMessage()); }
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<QuizError> auth(AuthFailure e) { return error(e.status(), e.code(), e.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class, HandlerMethodValidationException.class})
    ResponseEntity<QuizError> invalid(Exception e) { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu Quiz không hợp lệ."); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<QuizError> conflict(Exception e) { return error(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Quiz đã thay đổi."); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<QuizError> database(Exception e) { return error(HttpStatus.SERVICE_UNAVAILABLE, "QUIZ_UNAVAILABLE", "Chưa thể truy cập Quiz; thử lại sau."); }
    private ResponseEntity<QuizError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new QuizError(code, message, System.currentTimeMillis()));
    }
}
