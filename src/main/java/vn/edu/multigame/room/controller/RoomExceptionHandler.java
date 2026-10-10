package vn.edu.multigame.room.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.multigame.auth.service.AuthFailure;
import vn.edu.multigame.questionbank.service.QuestionBankFailure;
import vn.edu.multigame.room.dto.response.RoomError;
import vn.edu.multigame.room.service.RoomFailure;

@RestControllerAdvice(basePackages="vn.edu.multigame.room.controller") @Profile("mysql")
public class RoomExceptionHandler {
    @ExceptionHandler(RoomFailure.class) ResponseEntity<RoomError> room(RoomFailure e) { return error(e.status(),e.code(),e.retryable()); }
    @ExceptionHandler(QuestionBankFailure.class) ResponseEntity<RoomError> quiz(QuestionBankFailure e) { return error(e.status(),e.code(),false); }
    @ExceptionHandler(AuthFailure.class) ResponseEntity<RoomError> auth(AuthFailure e) { return error(e.status(),e.code(),false); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<RoomError> invalid(Exception e) { return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",false); }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<RoomError> database(Exception e) { return error(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE",true); }
    private ResponseEntity<RoomError> error(HttpStatus status,String code,boolean retryable) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new RoomError(code,code,retryable,System.currentTimeMillis()));
    }
}
