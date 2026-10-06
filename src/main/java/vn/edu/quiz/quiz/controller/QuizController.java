package vn.edu.quiz.quiz.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.quiz.quiz.dto.request.*;
import vn.edu.quiz.quiz.dto.response.*;
import vn.edu.quiz.quiz.service.QuizService;

@RestController
@RequestMapping("/api/quizzes")
@Profile("mysql")
public class QuizController {
    private final QuizService quizzes;
    public QuizController(QuizService quizzes) { this.quizzes = quizzes; }
    @PostMapping
    ResponseEntity<QuizOwnerResponse> create(Authentication auth, @Valid @RequestBody CreateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(quizzes.create(auth, request));
    }
    @GetMapping
    ResponseEntity<QuizListResponse> list(Authentication auth, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.list(auth, page, size));
    }
    @GetMapping("/{id}")
    ResponseEntity<Object> get(Authentication auth, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.get(auth, id));
    }
    @PutMapping("/{id}")
    ResponseEntity<QuizOwnerResponse> edit(Authentication auth, @PathVariable long id, @Valid @RequestBody EditQuizRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.edit(auth, id, request));
    }
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Authentication auth, @PathVariable long id, @RequestParam long revision) {
        quizzes.delete(auth, id, revision); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImageResponse> upload(Authentication auth, @PathVariable long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(quizzes.upload(auth, id, file));
    }
    @GetMapping("/{id}/images/{hash}")
    ResponseEntity<byte[]> image(Authentication auth, @PathVariable long id, @PathVariable String hash,
            @RequestParam(required = false) Long gameSessionId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(quizzes.image(auth, id, hash, gameSessionId));
    }
}
