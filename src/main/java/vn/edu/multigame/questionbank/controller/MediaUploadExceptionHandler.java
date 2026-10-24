package vn.edu.multigame.questionbank.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import vn.edu.multigame.questionbank.dto.response.QuestionBankError;

/** Multipart limits fail before MVC resolves a controller, so package-scoped advice cannot handle them. */
@RestControllerAdvice
@Profile("mysql")
public class MediaUploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<QuestionBankError> oversized(MaxUploadSizeExceededException exception, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).cacheControl(CacheControl.noStore())
                .body(new QuestionBankError("IMAGE_TOO_LARGE", "Ảnh tối đa 2 MiB.", System.currentTimeMillis()));
    }
}
