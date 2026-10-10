package vn.edu.multigame.questionbank.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import vn.edu.multigame.auth.service.AuthFailure;
import vn.edu.multigame.questionbank.dto.response.QuestionBankError;
import vn.edu.multigame.questionbank.service.QuestionBankFailure;

@RestControllerAdvice(basePackages = "vn.edu.multigame.questionbank.controller")
@Profile("mysql")
public class QuestionBankExceptionHandler {
    @ExceptionHandler(QuestionBankFailure.class)
    ResponseEntity<QuestionBankError> failure(QuestionBankFailure e) { return error(e.status(), e.code(), e.getMessage()); }
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<QuestionBankError> auth(AuthFailure e) { return error(e.status(), e.code(), e.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class, HandlerMethodValidationException.class})
    ResponseEntity<QuestionBankError> invalid(Exception e) { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu Quiz không hợp lệ."); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<QuestionBankError> conflict(Exception e) { return error(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Quiz đã thay đổi."); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<QuestionBankError> database(Exception e) { return error(HttpStatus.SERVICE_UNAVAILABLE, "QUIZ_UNAVAILABLE", "Chưa thể truy cập Quiz; thử lại sau."); }
    private ResponseEntity<QuestionBankError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new QuestionBankError(code, message, System.currentTimeMillis()));
    }
}
