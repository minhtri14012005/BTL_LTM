package vn.edu.multigame.academic.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.multigame.academic.service.AcademicFailure;
import vn.edu.multigame.auth.service.AuthFailure;
import vn.edu.multigame.auth.dto.response.AuthError;

@RestControllerAdvice(basePackages="vn.edu.multigame.academic.controller") @Profile("mysql")
public class AcademicExceptionHandler {
    @ExceptionHandler(AcademicFailure.class) ResponseEntity<AuthError> academic(AcademicFailure e) {return error(e.status(),e.getMessage());}
    @ExceptionHandler(AuthFailure.class) ResponseEntity<AuthError> auth(AuthFailure e) {return error(e.status(),e.code());}
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<AuthError> invalid(Exception e) {return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<AuthError> database(DataAccessException e) {
        for(Throwable cause=e;cause!=null;cause=cause.getCause()) {
            if(cause instanceof java.sql.SQLException sql) {
                if(sql.getErrorCode()==1062) return error(HttpStatus.CONFLICT,"DUPLICATE_RESOURCE");
                if(sql.getErrorCode()==1451) return error(HttpStatus.CONFLICT,"RESOURCE_IN_USE");
                if(sql.getErrorCode()==1452 || sql.getErrorCode()==3819) return error(HttpStatus.CONFLICT,"INVALID_RESOURCE");
            }
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE,"ACADEMIC_UNAVAILABLE");
    }
    private ResponseEntity<AuthError> error(HttpStatus status,String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new AuthError(code,code,System.currentTimeMillis()));
    }
}
