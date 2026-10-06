package vn.edu.quiz.quiz.service;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.quiz.auth.service.AuthService;
import vn.edu.quiz.quiz.dto.request.*;
import vn.edu.quiz.quiz.dto.response.*;
import vn.edu.quiz.quiz.entity.*;
import vn.edu.quiz.quiz.enums.*;
import vn.edu.quiz.quiz.repository.*;

@Service
@Profile("mysql")
@Transactional(readOnly = true)
public class QuizService {
    private final QuizRepository quizzes;
    private final QuestionRepository questions;
    private final AuthService users;
    private final QuizAccessPolicy policy;
    private final QuizImageStore images;
    private final EntityManager entities;
    public QuizService(QuizRepository quizzes, QuestionRepository questions, AuthService users,
            QuizAccessPolicy policy, QuizImageStore images, EntityManager entities) {
        this.quizzes = quizzes; this.questions = questions; this.users = users; this.policy = policy; this.images = images;
        this.entities = entities;
    }
    public QuizListResponse list(Authentication auth, int page, int size) {
        long userId = users.requireActiveUser(auth).getId();
        if (page < 0 || size < 1 || size > 100) throw invalidRequest();
        var found = quizzes.findVisible(userId, PageRequest.of(page, size));
        return new QuizListResponse(found.getContent().stream().map(this::metadata).toList(), page, size, found.getTotalElements());
    }
    public Object get(Authentication auth, long id) {
        long userId = users.requireActiveUser(auth).getId(); Quiz quiz = active(id, false);
        policy.requireUse(userId, quiz.getOwnerUserId(), quiz.getVisibility());
        return userId == quiz.getOwnerUserId() ? ownerView(quiz) : metadata(quiz);
    }
    /** Selection authorization for future Room use-case; no usage endpoint is invented. */
    public void requireUsable(Authentication auth, long id) {
        long userId = users.requireActiveUser(auth).getId(); Quiz quiz = active(id, false);
        policy.requireUse(userId, quiz.getOwnerUserId(), quiz.getVisibility());
    }
    @Transactional
    public QuizOwnerResponse create(Authentication auth, CreateQuizRequest request) {
        long userId = users.requireActiveUser(auth).getId();
        Quiz quiz = new Quiz(); quiz.setOwnerUserId(userId); quiz.setTitle(request.title().strip());
        quiz.setVisibility(request.visibility()); quiz.setCreatedAtMs(System.currentTimeMillis());
        quiz = quizzes.saveAndFlush(quiz);
        replaceQuestions(quiz, request.questions());
        return ownerView(quiz);
    }
    @Transactional
    public QuizOwnerResponse edit(Authentication auth, long id, EditQuizRequest request) {
        long userId = users.requireActiveUser(auth).getId(); Quiz quiz = active(id, true);
        policy.requireOwner(userId, quiz.getOwnerUserId()); requireRevision(quiz, request.revision());
        replaceQuestions(quiz, request.questions());
        quiz.setTitle(request.title().strip()); quiz.setVisibility(request.visibility());
        // A question-only edit must also increment aggregate revision.
        quizzes.flush();
        if (quiz.getRevision().equals(request.revision())) {
            // Force a real mapped change without manually changing the @Version field.
            if (quizzes.touchRevision(id, request.revision()) != 1) throw new QuizFailure(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Quiz đã thay đổi.");
            entities.refresh(quiz);
        }
        return ownerView(quiz);
    }
    @Transactional
    public void delete(Authentication auth, long id, long revision) {
        long userId = users.requireActiveUser(auth).getId(); Quiz quiz = active(id, true);
        policy.requireOwner(userId, quiz.getOwnerUserId()); requireRevision(quiz, revision);
        long now = Math.max(System.currentTimeMillis(), quiz.getCreatedAtMs());
        quiz.setDeletedAtMs(now);
        questions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(id).forEach(q -> q.setDeletedAtMs(Math.max(now, q.getCreatedAtMs())));
        quizzes.flush(); questions.flush();
    }
    @Transactional
    public ImageResponse upload(Authentication auth, long id, MultipartFile file) {
        long userId = users.requireActiveUser(auth).getId(); Quiz quiz = active(id, true);
        policy.requireOwner(userId, quiz.getOwnerUserId());
        String reference = images.save(id, file);
        return new ImageResponse(reference, "/api/quizzes/" + id + "/images/" + reference.substring(7), "image/png");
    }
    public byte[] image(Authentication auth, long id, String hash, Long gameSessionId) {
        long userId = users.requireActiveUser(auth).getId();
        Quiz quiz = quizzes.findById(id).orElseThrow(QuizFailure::notFound); // Deleted Quiz is retained for history images.
        String reference = "sha256:" + hash;
        if (!hash.matches("[0-9a-f]{64}")) throw invalidRequest();
        if (userId != quiz.getOwnerUserId() && (gameSessionId == null
                || questions.countReleasedImageForMember(id, gameSessionId, userId, reference) == 0)) {
            throw QuizFailure.forbidden();
        }
        return images.read(id, reference);
    }
    private Quiz active(long id, boolean lock) {
        return (lock ? quizzes.findLockedById(id) : quizzes.findById(id))
                .filter(q -> q.getDeletedAtMs() == null).orElseThrow(QuizFailure::notFound);
    }
    private void requireRevision(Quiz quiz, Long revision) {
        if (revision == null || revision < 0) throw invalidRequest();
        if (!quiz.getRevision().equals(revision)) throw new QuizFailure(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Quiz đã thay đổi; tải lại trước khi sửa.");
    }
    private void replaceQuestions(Quiz quiz, List<QuestionRequest> input) {
        // Never delete/update historical question content. New generation uses unused order indexes.
        input.forEach(q -> images.requireExists(quiz.getId(), q.imageRef()));
        int index = questions.maximumOrderIndex(quiz.getId());
        if (index > Integer.MAX_VALUE - input.size()) throw new QuizFailure(HttpStatus.CONFLICT, "QUIZ_LIMIT_REACHED", "Quiz đã đạt giới hạn phiên bản câu hỏi.");
        long now = System.currentTimeMillis();
        questions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(quiz.getId())
                .forEach(q -> q.setDeletedAtMs(Math.max(now, q.getCreatedAtMs())));
        for (QuestionRequest request : input) {
            Question question = new Question(); question.setQuizId(quiz.getId()); question.setOrderIndex(++index);
            question.setContent(request.content().strip());
            question.setOptionA(request.options().get(Option.A).strip()); question.setOptionB(request.options().get(Option.B).strip());
            question.setOptionC(request.options().get(Option.C).strip()); question.setOptionD(request.options().get(Option.D).strip());
            question.setCorrectOption(request.correctAnswer()); question.setImageRef(request.imageRef()); question.setCreatedAtMs(now);
            questions.save(question);
        }
        questions.flush();
    }
    private QuizMetadataResponse metadata(Quiz quiz) {
        return new QuizMetadataResponse(quiz.getId(), quiz.getOwnerUserId(), quiz.getTitle(), quiz.getVisibility(),
                (int) questions.countByQuizIdAndDeletedAtMsIsNull(quiz.getId()), quiz.getCreatedAtMs(), quiz.getRevision());
    }
    private QuizOwnerResponse ownerView(Quiz quiz) {
        var stored = questions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(quiz.getId());
        List<QuestionResponse> output = new ArrayList<>();
        for (Question q : stored) output.add(new QuestionResponse(q.getId(), output.size() + 1, q.getContent(),
                Map.of(Option.A, q.getOptionA(), Option.B, q.getOptionB(), Option.C, q.getOptionC(), Option.D, q.getOptionD()), q.getCorrectOption(), q.getImageRef()));
        return new QuizOwnerResponse(quiz.getId(), quiz.getOwnerUserId(), quiz.getTitle(), quiz.getVisibility(),
                output.size(), quiz.getCreatedAtMs(), quiz.getRevision(), List.copyOf(output));
    }
    private QuizFailure invalidRequest() { return new QuizFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu Quiz không hợp lệ."); }
}
