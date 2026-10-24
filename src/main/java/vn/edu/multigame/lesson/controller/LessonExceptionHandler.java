package vn.edu.multigame.lesson.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.auth.dto.response.AuthError;
import vn.edu.multigame.auth.service.AuthFailure;
import vn.edu.multigame.room.service.RoomFailure;
import vn.edu.multigame.lesson.service.LessonFailure;

@RestControllerAdvice(basePackages={"vn.edu.multigame.lesson.controller","vn.edu.multigame.leaderboard.controller"}) @Profile("mysql")
public class LessonExceptionHandler {
    @ExceptionHandler(vn.edu.multigame.questionbank.service.QuestionBankFailure.class)
    ResponseEntity<AuthError> bank(vn.edu.multigame.questionbank.service.QuestionBankFailure e) {return error(e.status(),e.code());}
    @ExceptionHandler(LessonFailure.class) ResponseEntity<AuthError> lesson(LessonFailure e) {return error(e.status(),e.getMessage());}
    @ExceptionHandler(AuthFailure.class) ResponseEntity<AuthError> auth(AuthFailure e) {return error(e.status(),e.code());}
    @ExceptionHandler(RoomFailure.class) ResponseEntity<AuthError> room(RoomFailure e) {return error(e.status(),e.code());}
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<AuthError> invalid(Exception e) {return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<AuthError> database(DataAccessException e) {
        for(Throwable cause=e;cause!=null;cause=cause.getCause()) if(cause instanceof java.sql.SQLException sql &&
                (sql.getErrorCode()==1062 || sql.getErrorCode()==1644 || sql.getErrorCode()==3819)) return error(HttpStatus.CONFLICT,"LESSON_CONSTRAINT");
        org.slf4j.LoggerFactory.getLogger(LessonExceptionHandler.class).warn("Lesson database operation failed",e);
        return error(HttpStatus.SERVICE_UNAVAILABLE,"LESSON_UNAVAILABLE");
    }
    @ExceptionHandler(java.util.concurrent.RejectedExecutionException.class)
    ResponseEntity<AuthError> busy(java.util.concurrent.RejectedExecutionException e) {return error(HttpStatus.SERVICE_UNAVAILABLE,"RUNTIME_BUSY");}
    private ResponseEntity<AuthError> error(HttpStatus status,String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new AuthError(code,code,System.currentTimeMillis()));
    }
}
