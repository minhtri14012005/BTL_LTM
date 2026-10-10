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
        boolean video=request.getRequestURI().contains("/videos");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).cacheControl(CacheControl.noStore())
                .body(new QuestionBankError(video?"VIDEO_TOO_LARGE":"IMAGE_TOO_LARGE", video?"Video tối đa20 MiB, multipart request tối đa21 MiB.":"Ảnh tối đa2 MiB.", System.currentTimeMillis()));
    }
}
