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
            @RequestParam(required = false) vn.edu.multigame.questionbank.enums.GameMode mode, @RequestParam(defaultValue="") String q, @RequestParam(required=false) Long subjectId, @RequestParam(required=false) Long classId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.list(auth, page, size, scope, mode, q, subjectId, classId));
    }
    @GetMapping("/subjects")
    ResponseEntity<java.util.List<vn.edu.multigame.academic.dto.AcademicViews.Subject>> subjects(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.subjects(auth));
    }
    @GetMapping("/{id}")
    ResponseEntity<Object> get(Authentication auth, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.get(auth, id));
    }
    @GetMapping("/{id}/preview")
    ResponseEntity<Object> preview(Authentication auth,@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.get(auth,id));
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
    ResponseEntity<byte[]> image(Authentication auth,@PathVariable long id,@PathVariable String hash){return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(quizzes.image(auth,id,hash));}
}
