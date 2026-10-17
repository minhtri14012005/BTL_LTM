package vn.edu.multigame.achievements.controller;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.game.service.GameFailure;
import vn.edu.multigame.auth.service.AuthFailure;
import vn.edu.multigame.room.dto.response.RoomError;
@RestControllerAdvice(basePackageClasses={GameAwardsController.class,vn.edu.multigame.leaderboard.controller.LeaderboardController.class}) @Profile("mysql")
public class AwardExceptionHandler {
    @ExceptionHandler(GameFailure.class) ResponseEntity<RoomError> game(GameFailure e){return error(e.status(),e.getMessage());}
    @ExceptionHandler(AuthFailure.class) ResponseEntity<RoomError> auth(AuthFailure e){return error(e.status(),e.code());}
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.transaction.TransactionException.class})
    ResponseEntity<RoomError> database(Exception e){return error(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE");}
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<RoomError> invalid(Exception e){return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");}
    private ResponseEntity<RoomError> error(HttpStatus status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new RoomError(code,code,status==HttpStatus.SERVICE_UNAVAILABLE,System.currentTimeMillis()));}
}
