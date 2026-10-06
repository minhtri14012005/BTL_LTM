package vn.edu.quiz.quiz.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import vn.edu.quiz.quiz.dto.response.QuizError;

/** Multipart limits fail before MVC resolves a controller, so package-scoped advice cannot handle them. */
@RestControllerAdvice
@Profile("mysql")
public class QuizUploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<QuizError> oversized(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).cacheControl(CacheControl.noStore())
                .body(new QuizError("IMAGE_TOO_LARGE", "Ảnh tối đa 2 MiB, multipart request tối đa3 MiB.", System.currentTimeMillis()));
    }
}
