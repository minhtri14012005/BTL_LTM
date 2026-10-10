package vn.edu.multigame.questionbank.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.dto.response.*;
import vn.edu.multigame.questionbank.service.QuestionBankService;

@RestController
@RequestMapping("/api/quizzes")
@Profile("mysql")
public class QuestionBankController {
    private final QuestionBankService quizzes;
    public QuestionBankController(QuestionBankService quizzes) { this.quizzes = quizzes; }
    @PostMapping
    ResponseEntity<QuestionBankOwnerResponse> create(Authentication auth, @Valid @RequestBody CreateQuestionBankRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(quizzes.create(auth, request));
    }
    @GetMapping
    ResponseEntity<QuestionBankListResponse> list(Authentication auth, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "VISIBLE") vn.edu.multigame.questionbank.enums.QuestionBankScope scope,
            @RequestParam(required = false) vn.edu.multigame.game.enums.GameMode mode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.list(auth, page, size, scope, mode));
    }
    @GetMapping("/{id}")
    ResponseEntity<Object> get(Authentication auth, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.get(auth, id));
    }
    @PutMapping("/{id}")
    ResponseEntity<QuestionBankOwnerResponse> edit(Authentication auth, @PathVariable long id, @Valid @RequestBody EditQuestionBankRequest request) {
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
    @PostMapping(value="/images/drafts", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImageResponse> uploadDraft(Authentication auth,@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(quizzes.uploadDraft(auth,file));
    }
    @GetMapping("/images/drafts/{hash}")
    ResponseEntity<byte[]> draftImage(Authentication auth,@PathVariable String hash) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(quizzes.draftImage(auth,hash));
    }
    @GetMapping("/{id}/images/{hash}")
    ResponseEntity<byte[]> image(Authentication auth, @PathVariable long id, @PathVariable String hash,
            @RequestParam(required = false) Long gameSessionId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(quizzes.image(auth, id, hash, gameSessionId));
    }
    @PostMapping(value="/{id}/videos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<VideoResponse> uploadVideo(Authentication auth,@PathVariable long id,@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(quizzes.uploadVideo(auth,id,file));
    }
    @PostMapping(value="/videos/drafts",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<VideoResponse> uploadDraftVideo(Authentication auth,@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(quizzes.uploadDraftVideo(auth,file));
    }
    @GetMapping("/videos/drafts/{hash}")
    ResponseEntity<org.springframework.core.io.Resource> draftVideo(Authentication auth,@PathVariable String hash) {
        return videoResponse(quizzes.draftVideo(auth,hash));
    }
    @GetMapping("/{id}/videos/{hash}")
    ResponseEntity<org.springframework.core.io.Resource> video(Authentication auth,@PathVariable long id,@PathVariable String hash,@RequestParam(required=false) Long gameSessionId) {
        return videoResponse(quizzes.video(auth,id,hash,gameSessionId));
    }
    private ResponseEntity<org.springframework.core.io.Resource> videoResponse(org.springframework.core.io.Resource resource) {
        return ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4")).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(resource);
    }
}
