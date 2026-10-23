package vn.edu.multigame.questionbank.service;

import java.util.*;
import vn.edu.multigame.common.util.TextNormalizer;
import vn.edu.multigame.common.util.ArrangementIds;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.dto.response.*;
import vn.edu.multigame.questionbank.entity.*;
import vn.edu.multigame.questionbank.enums.*;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.game.enums.GameMode;
import vn.edu.multigame.questionbank.repository.*;

@Service
@Profile("mysql")
@Transactional(readOnly = true)
public class QuestionBankService {
    private final QuizRepository quizzes;
    private final QuestionRepository questions;
    private final AuthService users;
    private final QuestionBankAccessPolicy policy;
    private final QuestionImageStore images;
    private final QuestionVideoStore videos;
    private final EntityManager entities;
    private final vn.edu.multigame.game.service.GameAccessPolicy gameAccess;
    private final vn.edu.multigame.auth.service.AccountAccessPolicy accounts;
    public QuestionBankService(QuizRepository quizzes, QuestionRepository questions, AuthService users,
            QuestionBankAccessPolicy policy, QuestionImageStore images, QuestionVideoStore videos, EntityManager entities,vn.edu.multigame.game.service.GameAccessPolicy gameAccess,vn.edu.multigame.auth.service.AccountAccessPolicy accounts) {
        this.quizzes = quizzes; this.questions = questions; this.users = users; this.policy = policy; this.images = images;
        this.entities = entities; this.videos=videos;this.gameAccess=gameAccess;
        this.accounts=accounts;
    }
    public QuestionBankListResponse list(Authentication auth, int page, int size) {
        return list(auth,page,size,QuestionBankScope.VISIBLE,null);
    }
    public QuestionBankListResponse list(Authentication auth,int page,int size,QuestionBankScope scope,GameMode mode) {
        return list(auth,page,size,scope,mode,"");
    }
    public QuestionBankListResponse list(Authentication auth,int page,int size,QuestionBankScope scope,GameMode mode,String query) {
        if(query==null || query.length()>200) throw invalidRequest();
        long userId = teacherId(auth);
        if (page < 0 || size < 1 || size > 100 || scope == null || (long)page*size > Integer.MAX_VALUE) throw invalidRequest();
        var found = quizzes.findScoped(userId, scope.name(), mode, query.strip(), PageRequest.of(page, size));
        return new QuestionBankListResponse(found.getContent().stream().map(this::metadata).toList(), page, size, found.getTotalElements());
    }
    private long teacherId(Authentication auth) {
        var user=users.requireActiveUser(auth);
        if(user.getRole()!=vn.edu.multigame.user.enums.UserRole.GIANG_VIEN) throw QuestionBankFailure.forbidden();
        return user.getId();
    }
    private long writingTeacher(Authentication auth) {
        long id=teacherId(auth);accounts.lock(id);return teacherId(auth);
    }
    public static boolean learningMode(GameMode mode) {return mode==GameMode.QUIZ || mode==GameMode.FILL_BLANK || mode==GameMode.ASSIGNMENT;}
    @Transactional
    public QuestionBankOwnerResponse previewLocked(Authentication auth,long id) {
        long user=teacherId(auth);var quiz=active(id,true);
        policy.requireUse(user,quiz.getOwnerUserId(),quiz.getVisibility());
        if(!learningMode(quiz.getMode())) throw invalidRequest();
        return ownerView(quiz);
    }
    public Object get(Authentication auth, long id) {
        long userId = teacherId(auth); Quiz quiz = active(id, false);
        policy.requireUse(userId, quiz.getOwnerUserId(), quiz.getVisibility());
        return ownerView(quiz);
    }
    /** Selection authorization for future Room use-case; no usage endpoint is invented. */
    public void requireUsable(Authentication auth, long id) {
        long userId = teacherId(auth); Quiz quiz = active(id, false);
        policy.requireUse(userId, quiz.getOwnerUserId(), quiz.getVisibility());
    }
    @Transactional
    public QuestionBankOwnerResponse create(Authentication auth, CreateQuestionBankRequest request) {
        long userId = writingTeacher(auth);
        GameMode mode=request.mode()==null?GameMode.QUIZ:request.mode();
        if(!learningMode(mode)) throw invalidRequest();
        validateQuestions(mode,request.questions());
        Quiz quiz = new Quiz(); quiz.setMode(mode); quiz.setOwnerUserId(userId); quiz.setTitle(request.title().strip());
        quiz.setVisibility(request.visibility()); quiz.setCreatedAtMs(System.currentTimeMillis());
        quiz = quizzes.saveAndFlush(quiz);
        replaceQuestions(quiz, request.questions());
        return ownerView(quiz);
    }
    @Transactional
    public QuestionBankOwnerResponse edit(Authentication auth, long id, EditQuestionBankRequest request) {
        long userId = writingTeacher(auth); Quiz quiz = active(id, true);
        policy.requireOwner(userId, quiz.getOwnerUserId()); requireRevision(quiz, request.revision());
        if(request.mode()!=null && request.mode()!=quiz.getMode()) throw new QuestionBankFailure(HttpStatus.BAD_REQUEST,"MODE_IMMUTABLE","Không đổi loại bộ câu đã tạo.");
        if(!learningMode(quiz.getMode())) throw invalidRequest();
        validateQuestions(quiz.getMode(),request.questions());
        replaceQuestions(quiz, request.questions());
        quiz.setTitle(request.title().strip()); quiz.setVisibility(request.visibility());
        // A question-only edit must also increment aggregate revision.
        quizzes.flush();
        if (quiz.getRevision().equals(request.revision())) {
            // Force a real mapped change without manually changing the @Version field.
            if (quizzes.touchRevision(id, request.revision()) != 1) throw new QuestionBankFailure(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Quiz đã thay đổi.");
            entities.refresh(quiz);
        }
        return ownerView(quiz);
    }
    @Transactional
    public void delete(Authentication auth, long id, long revision) {
        long userId = writingTeacher(auth); Quiz quiz = active(id, true);
        policy.requireOwner(userId, quiz.getOwnerUserId()); requireRevision(quiz, revision);
        long now = Math.max(System.currentTimeMillis(), quiz.getCreatedAtMs());
        quiz.setDeletedAtMs(now);
        questions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(id).forEach(q -> q.setDeletedAtMs(Math.max(now, q.getCreatedAtMs())));
        quizzes.flush(); questions.flush();
    }
    @Transactional
    public ImageResponse upload(Authentication auth, long id, MultipartFile file) {
        long userId = teacherId(auth); Quiz quiz = active(id, true);
        policy.requireOwner(userId, quiz.getOwnerUserId());
        String reference = images.save(id, file);
        return new ImageResponse(reference, "/api/quizzes/" + id + "/images/" + reference.substring(7), "image/png");
    }
    public ImageResponse uploadDraft(Authentication auth, MultipartFile file) {
        long userId=teacherId(auth);
        String reference=images.saveDraft(userId,file);
        return new ImageResponse(reference,"/api/quizzes/images/drafts/"+reference.substring(7),"image/png");
    }
    public byte[] draftImage(Authentication auth,String hash) {
        long userId=teacherId(auth);
        if(!hash.matches("[0-9a-f]{64}")) throw invalidRequest();
        return images.readDraft(userId,"sha256:"+hash);
    }
    public byte[] image(Authentication auth, long id, String hash, Long gameSessionId) {
        long userId = users.requireActiveUser(auth).getId();
        if(gameSessionId!=null)requireGameMedia(gameSessionId,userId);
        Quiz quiz = quizzes.findById(id).orElseThrow(QuestionBankFailure::notFound); // Deleted Quiz is retained for history images.
        String reference = "sha256:" + hash;
        if (!hash.matches("[0-9a-f]{64}")) throw invalidRequest();
        boolean teacher=users.requireActiveUser(auth).getRole()==vn.edu.multigame.user.enums.UserRole.GIANG_VIEN;
        if (!(teacher && (userId==quiz.getOwnerUserId() || quiz.getVisibility()==Visibility.PUBLIC)) && (gameSessionId == null
                || questions.countReleasedImageForMember(id, gameSessionId, userId, reference) == 0)) {
            throw QuestionBankFailure.forbidden();
        }
        return images.read(id, reference);
    }
    @Transactional
    public VideoResponse uploadVideo(Authentication auth,long id,MultipartFile file) {
        long userId=teacherId(auth);Quiz quiz=active(id,true);policy.requireOwner(userId,quiz.getOwnerUserId());
        if(quiz.getMode()!=GameMode.SONG)throw invalidRequest();return videos.save(id,file);
    }
    public VideoResponse uploadDraftVideo(Authentication auth,MultipartFile file){return videos.saveDraft(teacherId(auth),file);}
    public org.springframework.core.io.Resource draftVideo(Authentication auth,String hash){requireHash(hash);return videos.readDraft(teacherId(auth),"sha256:"+hash);}
    public org.springframework.core.io.Resource video(Authentication auth,long id,String hash,Long gameId) {
        long userId=users.requireActiveUser(auth).getId();requireHash(hash);Quiz quiz=quizzes.findById(id).orElseThrow(QuestionBankFailure::notFound);
        if(gameId!=null)requireGameMedia(gameId,userId);
        if(userId!=quiz.getOwnerUserId() && (gameId==null || questions.countReleasedVideoForMember(id,gameId,userId,"sha256:"+hash)==0))throw QuestionBankFailure.forbidden();
        return videos.read(id,"sha256:"+hash);
    }
    private void requireGameMedia(long game,long user){
        try{gameAccess.requireMediaAllowed(game,user);}catch(vn.edu.multigame.game.service.GameFailure denied){throw QuestionBankFailure.forbidden();}
    }
    private void requireHash(String hash){if(hash==null || !hash.matches("[0-9a-f]{64}"))throw invalidRequest();}
    private Quiz active(long id, boolean lock) {
        return (lock ? quizzes.findLockedById(id) : quizzes.findById(id))
                .filter(q -> q.getDeletedAtMs() == null).orElseThrow(QuestionBankFailure::notFound);
    }
    private void requireRevision(Quiz quiz, Long revision) {
        if (revision == null || revision < 0) throw invalidRequest();
        if (!quiz.getRevision().equals(revision)) throw new QuestionBankFailure(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Quiz đã thay đổi; tải lại trước khi sửa.");
    }
    private void replaceQuestions(Quiz quiz, List<QuestionRequest> input) {
        // Never delete/update historical question content. New generation uses unused order indexes.
        input.forEach(q -> images.requireExists(quiz.getId(), q.imageRef()));
        int index = questions.maximumOrderIndex(quiz.getId());
        if (index > Integer.MAX_VALUE - input.size()) throw new QuestionBankFailure(HttpStatus.CONFLICT, "QUIZ_LIMIT_REACHED", "Quiz đã đạt giới hạn phiên bản câu hỏi.");
        long now = System.currentTimeMillis();
        questions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(quiz.getId())
                .forEach(q -> q.setDeletedAtMs(Math.max(now, q.getCreatedAtMs())));
        for (QuestionRequest request : input) {
            Question question = new Question(); question.setQuizId(quiz.getId()); question.setOrderIndex(++index);
            question.setContent(request.content().strip());
            if(quiz.getMode()==GameMode.QUIZ) {
            question.setOptionA(request.options().get(Option.A).strip()); question.setOptionB(request.options().get(Option.B).strip());
            question.setOptionC(request.options().get(Option.C).strip()); question.setOptionD(request.options().get(Option.D).strip());
            question.setCorrectOption(request.correctAnswer());
            } else {
                question.setSchemaVersion(3);
                question.setPayload(quiz.getMode()==GameMode.ASSIGNMENT?Map.of():Map.of("acceptedAnswers",List.copyOf(request.acceptedAnswers()),"matchingPolicy",request.matchingPolicy().name()));
            }
            question.setMode(quiz.getMode()); question.setImageRef(request.imageRef()); question.setCreatedAtMs(now);
            questions.save(question);
        }
        questions.flush();
    }
    private QuestionBankMetadataResponse metadata(Quiz quiz) {
        return new QuestionBankMetadataResponse(quiz.getId(), quiz.getOwnerUserId(), quiz.getTitle(), quiz.getVisibility(),
                (int) questions.countByQuizIdAndDeletedAtMsIsNull(quiz.getId()), quiz.getCreatedAtMs(), quiz.getRevision(), quiz.getMode());
    }
    private QuestionBankOwnerResponse ownerView(Quiz quiz) {
        var stored = questions.findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(quiz.getId());
        List<QuestionResponse> output = new ArrayList<>();
        for (Question q : stored) output.add(new QuestionResponse(q.getId(), output.size() + 1, q.getContent(),
                quiz.getMode()==GameMode.QUIZ?Map.of(Option.A, q.getOptionA(), Option.B, q.getOptionB(), Option.C, q.getOptionC(), Option.D, q.getOptionD()):null, q.getCorrectOption(), q.getMode()==GameMode.IMAGE_WORD?(String)q.getPayload().get("mediaRef"):q.getImageRef(), quiz.getMode()==GameMode.CLUES || quiz.getMode()==GameMode.RIDDLE || quiz.getMode()==GameMode.IMAGE_WORD || quiz.getMode()==GameMode.SONG || quiz.getMode()==GameMode.VIETNAMESE_PUZZLE || quiz.getMode()==GameMode.FILL_BLANK?storedAnswers(q):null, storedItems(q,"pieces"),storedItems(q,"items"),storedOrder(q),q.getMode()==GameMode.SONG?(String)q.getPayload().get("mediaRef"):null,storedHints(q),q.getMode()==GameMode.FILL_BLANK?TextMatchPolicy.valueOf((String)q.getPayload().get("matchingPolicy")):null));
        return new QuestionBankOwnerResponse(quiz.getId(), quiz.getOwnerUserId(), quiz.getTitle(), quiz.getVisibility(),
                output.size(), quiz.getCreatedAtMs(), quiz.getRevision(), List.copyOf(output),quiz.getMode());
    }
    @SuppressWarnings("unchecked")
    private List<HintRequest> storedHints(Question q) {
        if(q.getMode()!=GameMode.CLUES) return null;
        return ((List<Map<String,Object>>)q.getPayload().get("hints")).stream().map(h -> new HintRequest(((Number)h.get("offsetMs")).longValue(),(String)h.get("text"))).toList();
    }
    private List<String> storedAnswers(Question question) {
        Object value=question.getPayload().get("acceptedAnswers");
        if(!(value instanceof List<?>)) {
            if(question.getMode()==GameMode.VIETNAMESE_PUZZLE) {var text=new HashMap<String,String>();storedItems(question,"pieces").forEach(v -> text.put(v.id(),v.text()));return List.of(TextNormalizer.normalize(storedOrder(question).stream().map(text::get).collect(java.util.stream.Collectors.joining())));}
            throw new IllegalStateException("Invalid stored text answers");
        }
        var answers=(List<?>)value;
        return answers.stream().map(String.class::cast).toList();
    }
    @SuppressWarnings("unchecked")
    private List<ArrangementItemResponse> storedItems(Question q,String key) {
        if(q.getPayload()==null || !(q.getPayload().get(key) instanceof List<?> data)) return null;
        return data.stream().map(v -> {var m=(Map<String,Object>)v;return new ArrangementItemResponse((String)m.get("id"),(String)m.get("text"));}).toList();
    }
    @SuppressWarnings("unchecked")
    private List<String> storedOrder(Question q) {return q.getPayload()==null?null:(List<String>)q.getPayload().get("correctOrder");}
    public void validateQuestions(GameMode mode,List<QuestionRequest> input) {
        if(!learningMode(mode) || input==null || input.isEmpty() || input.size()>50 || input.stream().anyMatch(Objects::isNull)) throw invalidRequest();
        if(mode==GameMode.ASSIGNMENT && input.size()!=1) throw invalidRequest();
        for(var q:input) {
            if(q.content()==null || q.content().isBlank() || q.content().length()>5000) throw invalidRequest();
            if(mode==GameMode.FILL_BLANK || mode==GameMode.ASSIGNMENT) {
                if(q.options()!=null || q.correctAnswer()!=null || q.imageRef()!=null || q.mediaRef()!=null || q.pieces()!=null || q.items()!=null || q.correctOrder()!=null || q.hints()!=null) throw invalidRequest();
                if(mode==GameMode.ASSIGNMENT) {
                    if(q.acceptedAnswers()!=null || q.matchingPolicy()!=null) throw invalidRequest();
                } else {
                    if(q.content().indexOf("{{blank}}")<0 || q.content().indexOf("{{blank}}")!=q.content().lastIndexOf("{{blank}}") || q.matchingPolicy()==null || q.acceptedAnswers()==null || q.acceptedAnswers().isEmpty() || q.acceptedAnswers().size()>20 || q.acceptedAnswers().stream().anyMatch(a->a==null || a.isBlank() || a.length()>300)) throw invalidRequest();
                }
                continue;
            }
            if(mode!=GameMode.QUIZ || q.matchingPolicy()!=null || q.hints()!=null || q.mediaRef()!=null || q.pieces()!=null || q.items()!=null || q.correctOrder()!=null || q.acceptedAnswers()!=null || q.options()==null || !q.options().keySet().equals(EnumSet.allOf(Option.class)) || q.correctAnswer()==null || q.options().values().stream().anyMatch(v->v==null || v.isBlank() || v.length()>2000)) throw invalidRequest();
        }
    }
    public List<QuestionResponse> snapshotQuestions(GameMode mode,List<QuestionRequest> input) {
        if(!learningMode(mode)) throw invalidRequest();
        validateQuestions(mode,input);
        var output=new ArrayList<QuestionResponse>();
        for(var q:input) output.add(new QuestionResponse(0,output.size()+1,q.content(),q.options(),q.correctAnswer(),q.imageRef(),
                q.acceptedAnswers(),null,null,null,null,null,q.matchingPolicy()));
        return List.copyOf(output);
    }
    private QuestionBankFailure invalidRequest() { return new QuestionBankFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu Quiz không hợp lệ."); }
}
