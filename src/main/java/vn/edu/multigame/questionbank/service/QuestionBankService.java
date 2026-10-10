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
    public QuestionBankService(QuizRepository quizzes, QuestionRepository questions, AuthService users,
            QuestionBankAccessPolicy policy, QuestionImageStore images, QuestionVideoStore videos, EntityManager entities) {
        this.quizzes = quizzes; this.questions = questions; this.users = users; this.policy = policy; this.images = images;
        this.entities = entities; this.videos=videos;
    }
    public QuestionBankListResponse list(Authentication auth, int page, int size) {
        return list(auth,page,size,QuestionBankScope.VISIBLE,null);
    }
    public QuestionBankListResponse list(Authentication auth,int page,int size,QuestionBankScope scope,GameMode mode) {
        long userId = users.requireActiveUser(auth).getId();
        if (page < 0 || size < 1 || size > 100 || scope == null || (long)page*size > Integer.MAX_VALUE) throw invalidRequest();
        var found = quizzes.findScoped(userId, scope.name(), mode, PageRequest.of(page, size));
        return new QuestionBankListResponse(found.getContent().stream().map(this::metadata).toList(), page, size, found.getTotalElements());
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
    public QuestionBankOwnerResponse create(Authentication auth, CreateQuestionBankRequest request) {
        long userId = users.requireActiveUser(auth).getId();
        GameMode mode=request.mode()==null?GameMode.QUIZ:request.mode();
        validateQuestions(mode,request.questions());
        Quiz quiz = new Quiz(); quiz.setMode(mode); quiz.setOwnerUserId(userId); quiz.setTitle(request.title().strip());
        quiz.setVisibility(request.visibility()); quiz.setCreatedAtMs(System.currentTimeMillis());
        quiz = quizzes.saveAndFlush(quiz);
        replaceQuestions(quiz, request.questions());
        return ownerView(quiz);
    }
    @Transactional
    public QuestionBankOwnerResponse edit(Authentication auth, long id, EditQuestionBankRequest request) {
        long userId = users.requireActiveUser(auth).getId(); Quiz quiz = active(id, true);
        policy.requireOwner(userId, quiz.getOwnerUserId()); requireRevision(quiz, request.revision());
        if(request.mode()!=null && request.mode()!=quiz.getMode()) throw new QuestionBankFailure(HttpStatus.BAD_REQUEST,"MODE_IMMUTABLE","Không đổi loại bộ câu đã tạo.");
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
    public ImageResponse uploadDraft(Authentication auth, MultipartFile file) {
        long userId=users.requireActiveUser(auth).getId();
        String reference=images.saveDraft(userId,file);
        return new ImageResponse(reference,"/api/quizzes/images/drafts/"+reference.substring(7),"image/png");
    }
    public byte[] draftImage(Authentication auth,String hash) {
        long userId=users.requireActiveUser(auth).getId();
        if(!hash.matches("[0-9a-f]{64}")) throw invalidRequest();
        return images.readDraft(userId,"sha256:"+hash);
    }
    public byte[] image(Authentication auth, long id, String hash, Long gameSessionId) {
        long userId = users.requireActiveUser(auth).getId();
        Quiz quiz = quizzes.findById(id).orElseThrow(QuestionBankFailure::notFound); // Deleted Quiz is retained for history images.
        String reference = "sha256:" + hash;
        if (!hash.matches("[0-9a-f]{64}")) throw invalidRequest();
        if (userId != quiz.getOwnerUserId() && (gameSessionId == null
                || questions.countReleasedImageForMember(id, gameSessionId, userId, reference) == 0)) {
            throw QuestionBankFailure.forbidden();
        }
        return images.read(id, reference);
    }
    @Transactional
    public VideoResponse uploadVideo(Authentication auth,long id,MultipartFile file) {
        long userId=users.requireActiveUser(auth).getId();Quiz quiz=active(id,true);policy.requireOwner(userId,quiz.getOwnerUserId());
        if(quiz.getMode()!=GameMode.SONG)throw invalidRequest();return videos.save(id,file);
    }
    public VideoResponse uploadDraftVideo(Authentication auth,MultipartFile file){return videos.saveDraft(users.requireActiveUser(auth).getId(),file);}
    public org.springframework.core.io.Resource draftVideo(Authentication auth,String hash){requireHash(hash);return videos.readDraft(users.requireActiveUser(auth).getId(),"sha256:"+hash);}
    public org.springframework.core.io.Resource video(Authentication auth,long id,String hash,Long gameId) {
        long userId=users.requireActiveUser(auth).getId();requireHash(hash);Quiz quiz=quizzes.findById(id).orElseThrow(QuestionBankFailure::notFound);
        if(userId!=quiz.getOwnerUserId() && (gameId==null || questions.countReleasedVideoForMember(id,gameId,userId,"sha256:"+hash)==0))throw QuestionBankFailure.forbidden();
        return videos.read(id,"sha256:"+hash);
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
        input.forEach(q -> {
            if(quiz.getMode()==GameMode.SONG) videos.attachDraft(quiz.getId(),quiz.getOwnerUserId(),q.mediaRef());
            else if(quiz.getMode()==GameMode.IMAGE_WORD) images.attachDraft(quiz.getId(),quiz.getOwnerUserId(),q.imageRef());
            else images.requireExists(quiz.getId(), q.imageRef());
        });
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
                question.setSchemaVersion(2);
                if(quiz.getMode()==GameMode.CLUES || quiz.getMode()==GameMode.RIDDLE || quiz.getMode()==GameMode.IMAGE_WORD || quiz.getMode()==GameMode.SONG) {
                    var payload=new LinkedHashMap<String,Object>();
                    if(quiz.getMode()==GameMode.CLUES) payload.put("hints",request.hints().stream().map(h -> Map.of("offsetMs",h.offsetMs(),"text",h.text().strip())).toList());
                    payload.put("acceptedAnswers",normalizedAnswers(request.acceptedAnswers()));payload.put("matchingPolicy",TextNormalizer.POLICY);
                    if(quiz.getMode()==GameMode.IMAGE_WORD) payload.put("mediaRef",request.imageRef());
                    if(quiz.getMode()==GameMode.SONG) payload.put("mediaRef",request.mediaRef());
                    question.setPayload(payload);
                }
                else {
                    var payload=new LinkedHashMap<String,Object>();
                    var values=quiz.getMode()==GameMode.VIETNAMESE_PUZZLE?request.pieces():request.items();
                    payload.put(quiz.getMode()==GameMode.VIETNAMESE_PUZZLE?"pieces":"items",values.stream().map(v -> Map.of("id",v.id(),"text",v.text())).toList());
                    payload.put("correctOrder",request.correctOrder());
                    if(quiz.getMode()==GameMode.VIETNAMESE_PUZZLE) {payload.put("acceptedAnswers",normalizedAnswers(request.acceptedAnswers()));payload.put("matchingPolicy",TextNormalizer.POLICY);}
                    question.setPayload(payload);
                }
            }
            question.setMode(quiz.getMode()); question.setImageRef(quiz.getMode()==GameMode.IMAGE_WORD?null:request.imageRef()); question.setCreatedAtMs(now);
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
                quiz.getMode()==GameMode.QUIZ?Map.of(Option.A, q.getOptionA(), Option.B, q.getOptionB(), Option.C, q.getOptionC(), Option.D, q.getOptionD()):null, q.getCorrectOption(), q.getMode()==GameMode.IMAGE_WORD?(String)q.getPayload().get("mediaRef"):q.getImageRef(), quiz.getMode()==GameMode.CLUES || quiz.getMode()==GameMode.RIDDLE || quiz.getMode()==GameMode.IMAGE_WORD || quiz.getMode()==GameMode.SONG || quiz.getMode()==GameMode.VIETNAMESE_PUZZLE?storedAnswers(q):null, storedItems(q,"pieces"),storedItems(q,"items"),storedOrder(q),q.getMode()==GameMode.SONG?(String)q.getPayload().get("mediaRef"):null,storedHints(q)));
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
    private List<String> normalizedAnswers(List<String> input) {
        return input.stream().map(TextNormalizer::normalize).distinct().toList();
    }
    @SuppressWarnings("unchecked")
    private List<ArrangementItemResponse> storedItems(Question q,String key) {
        if(q.getPayload()==null || !(q.getPayload().get(key) instanceof List<?> data)) return null;
        return data.stream().map(v -> {var m=(Map<String,Object>)v;return new ArrangementItemResponse((String)m.get("id"),(String)m.get("text"));}).toList();
    }
    @SuppressWarnings("unchecked")
    private List<String> storedOrder(Question q) {return q.getPayload()==null?null:(List<String>)q.getPayload().get("correctOrder");}
    private void validateQuestions(GameMode mode,List<QuestionRequest> input) {
        for(var q:input) {
            if(mode==GameMode.CLUES) {
                if(q.hints()==null || q.hints().isEmpty() || q.hints().size()>20) throw invalidRequest();
                long previous=-1;
                for(var hint:q.hints()) {
                    if(hint==null || hint.offsetMs()==null || hint.offsetMs()<0 || hint.offsetMs()<=previous || hint.text()==null || hint.text().isBlank() || hint.text().length()>2000) throw invalidRequest();
                    previous=hint.offsetMs();
                }
            } else if(q.hints()!=null) throw invalidRequest();
            if(mode==GameMode.SONG?q.mediaRef()==null:q.mediaRef()!=null)throw invalidRequest();
            boolean arrangement=mode==GameMode.VIETNAMESE_PUZZLE || mode==GameMode.ORDERING;
            if(!arrangement && (q.pieces()!=null || q.items()!=null || q.correctOrder()!=null)) throw invalidRequest();
            if(mode==GameMode.QUIZ) {
                if(q.options()==null || !q.options().keySet().equals(EnumSet.allOf(Option.class)) || q.correctAnswer()==null || q.acceptedAnswers()!=null) throw invalidRequest();
            } else if(mode==GameMode.CLUES || mode==GameMode.RIDDLE || mode==GameMode.IMAGE_WORD || mode==GameMode.SONG) {
                if(q.options()!=null || q.correctAnswer()!=null || (mode==GameMode.IMAGE_WORD?q.imageRef()==null:q.imageRef()!=null) || q.acceptedAnswers()==null
                        || normalizedAnswers(q.acceptedAnswers()).stream().anyMatch(a -> a.isEmpty() || a.length()>300)) throw invalidRequest();
            } else {
                var values=mode==GameMode.VIETNAMESE_PUZZLE?q.pieces():q.items();
                if(q.options()!=null || q.correctAnswer()!=null || q.imageRef()!=null || values==null
                        || mode==GameMode.VIETNAMESE_PUZZLE && q.items()!=null || mode==GameMode.ORDERING && q.pieces()!=null
                        || !ArrangementIds.permutation(values.stream().map(ArrangementItemRequest::id).toList(),q.correctOrder())) throw invalidRequest();
                if(mode==GameMode.ORDERING) {
                    if(q.acceptedAnswers()!=null || values.stream().anyMatch(v -> v.text().isBlank())) throw invalidRequest();
                } else {
                    if(q.acceptedAnswers()==null || normalizedAnswers(q.acceptedAnswers()).stream().anyMatch(a -> a.isEmpty() || a.length()>300)) throw invalidRequest();
                    Map<String,String> text=new HashMap<>();values.forEach(v -> text.put(v.id(),v.text()));
                    String joined=q.correctOrder().stream().map(text::get).collect(java.util.stream.Collectors.joining(""));
                    if(!normalizedAnswers(q.acceptedAnswers()).contains(TextNormalizer.normalize(joined))) throw invalidRequest();
                }
            }
        }
    }
    private QuestionBankFailure invalidRequest() { return new QuestionBankFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu Quiz không hợp lệ."); }
}
