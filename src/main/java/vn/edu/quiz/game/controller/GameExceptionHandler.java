package vn.edu.quiz.game.controller;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.quiz.auth.service.AuthFailure;
import vn.edu.quiz.quiz.service.QuizFailure;
import vn.edu.quiz.room.service.RoomFailure;
import vn.edu.quiz.room.dto.response.RoomError;
import vn.edu.quiz.game.service.GameFailure;
@RestControllerAdvice(basePackages="vn.edu.quiz.game.controller") @Profile("mysql")
public class GameExceptionHandler {
    @ExceptionHandler(GameFailure.class) ResponseEntity<RoomError> game(GameFailure e) { return error(e.status(),e.getMessage()); }
    @ExceptionHandler(RoomFailure.class) ResponseEntity<RoomError> room(RoomFailure e) {
        return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore()).body(new RoomError(e.code(),e.code(),e.retryable(),System.currentTimeMillis()));
    }
    @ExceptionHandler(AuthFailure.class) ResponseEntity<RoomError> auth(AuthFailure e) { return error(e.status(),e.code()); }
    @ExceptionHandler(QuizFailure.class) ResponseEntity<RoomError> quiz(QuizFailure e) { return error(e.status(),e.code()); }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.transaction.TransactionException.class,java.util.concurrent.RejectedExecutionException.class})
    ResponseEntity<RoomError> database(Exception e) { return error(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE"); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<RoomError> invalid(Exception e) { return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST"); }
    private ResponseEntity<RoomError> error(HttpStatus status,String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new RoomError(code,code,status==HttpStatus.SERVICE_UNAVAILABLE,System.currentTimeMillis()));
    }
}
