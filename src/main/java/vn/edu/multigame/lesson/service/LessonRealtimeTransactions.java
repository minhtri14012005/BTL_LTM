package vn.edu.multigame.lesson.service;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.user.enums.UserRole;
import vn.edu.multigame.lesson.repository.*;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.realtime.message.command.LessonCommand;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;
import vn.edu.multigame.realtime.timer.ServerClock;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.realtime.session.Ingress;
import vn.edu.multigame.questionbank.dto.response.StudentQuestion;

@Service @Profile("mysql") @Transactional(readOnly=true)
public class LessonRealtimeTransactions {
    public record Result(LessonRealtimeView snapshot,long sequence,long receivedAtMs,boolean replay,PhaseWindow phase) {}
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
    private final AssignmentStore assignments;
    private final QuizPlayStore quiz;
    private final WheelService wheel;
    private final LessonService lessons;
    private final LessonStore store;
    private final LessonRealtimeStore realtime;
    private final GameContentStore content;
    private final AuthService auth;
    private final AccountAccessPolicy accounts;
    private final CommandFingerprint fingerprints;
    private final ServerClock clock;
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.questionbank.service.QuestionImageStore images;
    public LessonRealtimeTransactions(LessonService lessons,LessonStore store,LessonRealtimeStore realtime,GameContentStore content,
            AuthService auth,AccountAccessPolicy accounts,CommandFingerprint fingerprints,ServerClock clock,WheelService wheel,QuizPlayStore quiz,AssignmentStore assignments) {
        this.assignments=assignments;this.quiz=quiz;this.wheel=wheel;this.lessons=lessons;this.store=store;this.realtime=realtime;this.content=content;this.auth=auth;this.accounts=accounts;this.fingerprints=fingerprints;this.clock=clock;
    }
    public LessonRealtimeView snapshot(Authentication caller,long lesson) {
        var user=auth.requireActiveUser(caller);boolean teacher=user.getRole()==UserRole.GIANG_VIEN;
        var view=lessons.get(caller,lesson,teacher);
        var window=realtime.window(lesson).orElse(null);
        LessonRealtimeView.Window publicWindow=null;
        if(window!=null && view.games().stream().anyMatch(g->g.id()==window.gameId())) {
            var game=view.games().stream().filter(g->g.id()==window.gameId()).findFirst().orElseThrow();
            StudentQuestion question=null;
            if((window.status().equals("OPEN") || game.mode()==vn.edu.multigame.lesson.domain.LessonMode.ASSIGNMENT) && (teacher || game.participating())) {
                var questions=content.get(game.id()).map(GameContentView::questions).orElse(java.util.List.of());
                if(window.questionIndex()<=questions.size()) question=StudentQuestion.from(questions.get(window.questionIndex()-1));
            }
            publicWindow=new LessonRealtimeView.Window(game.id(),game.position(),window.token(),window.questionIndex(),window.status(),window.opened(),window.deadline(),realtime.submitted(game.id(),window.token(),user.getId()),question,questionCount(game));
        }
        return new LessonRealtimeView(view,publicWindow,clock.sample().epochMs(),view.games().stream().anyMatch(g->g.position()==1)?wheel.view(lesson,user.getId(),teacher):null,view.games().stream().filter(g->g.mode()==vn.edu.multigame.lesson.domain.LessonMode.QUIZ).collect(java.util.stream.Collectors.toMap(g->Long.toString(g.id()),g->quiz.results(g.id(),user.getId()))),view.games().stream().filter(g->g.mode()==vn.edu.multigame.lesson.domain.LessonMode.FILL_BLANK).collect(java.util.stream.Collectors.toMap(g->Long.toString(g.id()),g->quiz.results(g.id(),user.getId()))),view.games().stream().filter(g->g.mode()==vn.edu.multigame.lesson.domain.LessonMode.ASSIGNMENT).collect(java.util.stream.Collectors.toMap(g->Long.toString(g.id()),g->assignments.view(g.id(),teacher?store.participants(g.id()):java.util.List.of(user.getId())))),teacher?lessons.participants(caller,lesson):java.util.List.of(),view.games().stream().collect(java.util.stream.Collectors.toMap(g->Long.toString(g.id()),g->questionCount(g))));
    }
    private int questionCount(LessonView.Game game){return content.get(game.id()).map(c->GameSettingsService.usedCount(game,c.questions().size())).orElse(0);}
    public boolean canDeliver(Authentication caller,long lesson,LessonRealtimeView pending){
        try{var current=lessons.get(caller,lesson,auth.requireActiveUser(caller).getRole()==UserRole.GIANG_VIEN);return current.games().stream().map(LessonView.Game::id).toList().containsAll(pending.lesson().games().stream().map(LessonView.Game::id).toList());}
        catch(RuntimeException denied){return false;}
    }
    public byte[] image(Authentication caller,long lesson,String hash,long token){
        var window=snapshot(caller,lesson).window();
        if(window==null || window.token()!=token || window.question()==null || !("sha256:"+hash).equals(window.question().imageRef()))throw new LessonFailure(org.springframework.http.HttpStatus.NOT_FOUND,"IMAGE_NOT_AVAILABLE");
        var source=content.get(window.gameId()).orElseThrow(()->new LessonFailure(org.springframework.http.HttpStatus.NOT_FOUND,"IMAGE_NOT_AVAILABLE"));
        return images.read(source.sourceQuizId(),"sha256:"+hash);
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public Result execute(Authentication caller,LessonCommand command,Ingress<?> ingress,PhaseWindow phase,Runnable session) {
        session.run();var user=auth.requireActiveUser(caller);long id=command.target().id();
        if(java.util.Set.of("SET_ASSIGNMENT_DEADLINE","GRADE_ASSIGNMENT","FINALIZE_ASSIGNMENT").contains(command.type()))lessons.get(caller,id,true);
        var locked=new java.util.TreeSet<Long>();locked.add(user.getId());
        if(command.type().equals("GRADE_ASSIGNMENT"))locked.add(command.payload().path("studentId").longValue());
        if(command.type().equals("FINALIZE_ASSIGNMENT"))locked.addAll(store.participants(assignmentGame(id,command.payload().path("position").intValue()).id()));
        if(java.util.Set.of("CLOSE_LESSON_WINDOW","CANCEL_LESSON_WINDOW").contains(command.type()))realtime.window(id).ifPresent(w->locked.addAll(store.participants(w.gameId())));
        for(long account:locked)accounts.lock(account);
        session.run();auth.requireActiveUser(caller);store.lesson(id,true);
        var before=snapshot(caller,id);String type=command.type();var payload=command.payload();
        boolean teacher=java.util.Set.of("SET_ASSIGNMENT_DEADLINE","GRADE_ASSIGNMENT","FINALIZE_ASSIGNMENT","START_LESSON","START_LESSON_GAME","OPEN_LESSON_WINDOW","CLOSE_LESSON_WINDOW","CANCEL_LESSON_WINDOW").contains(type);
        boolean student=java.util.Set.of("ATTEND_LESSON","PARTICIPATE_LESSON_GAME","SUBMIT_LESSON_ACTION").contains(type);
        if(teacher && user.getRole()!=UserRole.GIANG_VIEN || student && user.getRole()!=UserRole.SINH_VIEN) throw new LessonFailure(org.springframework.http.HttpStatus.FORBIDDEN,"ROLE_FORBIDDEN");
        String hash=fingerprints.of(type,"LESSON",id,null,payload);
        var prior=realtime.receipt(user.getId(),command.requestId());
        if(prior.isPresent()) {
            if(!prior.get().fingerprint().equals(hash)) throw LessonFailure.conflict("REQUEST_ID_REUSED");
            return new Result(before,prior.get().sequence(),prior.get().receivedAtMs(),true,phase);
        }
        PhaseWindow active=phase;
        switch(type) {
            case "SET_ASSIGNMENT_DEADLINE" -> {
                var game=assignmentGame(id,payload.path("position").intValue());
                boolean draft=game.status().equals("READY");if(!draft)runningAssignment(before,game);
                content.get(game.id()).orElseThrow(()->LessonFailure.conflict("GAME_CONTENT_REQUIRED"));
                var old=realtime.window(id);long expected=old.map(w->w.token()).orElse(0L);
                if(expected!=payload.path("token").longValue())throw LessonFailure.conflict("STALE_WINDOW");
                var now=clock.sample();long deadline=payload.path("deadlineAtMs").longValue();
                if(deadline-now.epochMs()>Integer.MAX_VALUE)throw LessonFailure.conflict("INVALID_DEADLINE");
                assignments.deadline(game.id(),deadline);if(draft){store.bump(id);break;}
                long opened=Math.min(now.epochMs(),deadline-1);long token=expected+1;
                realtime.open(id,game.id(),game.position(),token,1,opened,deadline);
                if(deadline<=now.epochMs()){realtime.close(id,token,"CLOSED");active=null;}
                else active=PhaseWindow.open(1,vn.edu.multigame.game.enums.Phase.QUESTION_OPEN,token,now.monotonicMs(),now.epochMs(),deadline-now.epochMs());
                store.bump(id);
            }
            case "GRADE_ASSIGNMENT" -> {
                var game=assignmentGame(id,payload.path("position").intValue());runningAssignment(before,game);
                long studentId=payload.path("studentId").longValue();
                var latest=assignments.latest(game.id(),studentId).orElseThrow(()->LessonFailure.conflict("SUBMISSION_NOT_FOUND"));
                if(latest.id()!=payload.path("submissionId").longValue())throw LessonFailure.conflict("STALE_SUBMISSION");
                var score=payload.path("score").decimalValue();if(score.signum()<0 || score.scale()>2 || score.compareTo(game.budget())>0)throw LessonFailure.conflict("INVALID_GAME_SCORE");
                assignments.grade(game.id(),studentId,latest.id(),score,clock.sample().epochMs());store.bump(id);
            }
            case "FINALIZE_ASSIGNMENT" -> {
                var game=assignmentGame(id,payload.path("position").intValue());runningAssignment(before,game);
                var window=realtime.window(id).orElseThrow(()->LessonFailure.conflict("DEADLINE_REQUIRED"));
                if(window.gameId()!=game.id() || window.status().equals("OPEN"))throw LessonFailure.conflict("SUBMISSIONS_STILL_OPEN");
                var participants=store.participants(game.id());if(!locked.containsAll(participants))throw LessonFailure.conflict("PARTICIPANTS_CHANGED_RETRY");
                var room=memberships.forClass(before.lesson().courseClassId()).orElseThrow();
                for(long studentId:participants){
                    java.math.BigDecimal score=java.math.BigDecimal.ZERO;
                    if(!memberships.excluded(studentId,room) && !accounts.state(studentId).banned(clock.sample().epochMs())) {
                        var latest=assignments.latest(game.id(),studentId);
                        if(latest.isPresent()){
                            var grade=assignments.grade(game.id(),studentId).orElseThrow(()->LessonFailure.conflict("SUBMISSION_NOT_GRADED"));
                            if(grade.submissionId()!=latest.get().id())throw LessonFailure.conflict("STALE_SUBMISSION_GRADE");score=grade.score();
                        }
                    }
                    store.score(game.id(),studentId,score.setScale(2));store.total(id,studentId);
                }
                store.gameStatus(game.id(),"COMPLETED");store.finishIfReady(id);events.publishEvent(new LessonChanged(id));active=null;
            }
            case "START_LESSON" -> lessons.start(caller,id,payload.path("revision").longValue());
            case "START_LESSON_GAME" -> {
                int startPosition=payload.path("position").intValue();
                var startedLesson=lessons.startGame(caller,id,startPosition,payload.path("revision").longValue());
                for(var game:startedLesson.games()) if(game.position()==startPosition && objective(game.mode())) {
                    var opened=openFirstWindow(id,game.id(),startPosition);if(opened!=null)active=opened;
                }
            }
            case "ATTEND_LESSON" -> lessons.attend(caller,id,null);
            case "PARTICIPATE_LESSON_GAME" -> lessons.attend(caller,id,payload.path("position").intValue());
            case "OPEN_LESSON_WINDOW" -> {
                var old=realtime.window(id);
                if(old.isPresent() && old.get().status().equals("OPEN")) throw LessonFailure.conflict("WINDOW_ALREADY_OPEN");
                int position=payload.path("position").intValue(),index=payload.path("questionIndex").intValue();
                var game=before.lesson().games().stream().filter(g->g.position()==position).findFirst().orElseThrow();
                if(objective(game.mode()))throw LessonFailure.conflict("AUTOMATIC_GAME_CONTROL");
                if(!before.lesson().status().equals("RUNNING") || !game.status().equals("RUNNING")) throw LessonFailure.conflict("GAME_NOT_RUNNING");
                if(game.mode()!=vn.edu.multigame.lesson.domain.LessonMode.LUCKY_WHEEL) {
                    var questions=content.get(game.id()).orElseThrow(()->LessonFailure.conflict("GAME_CONTENT_REQUIRED")).questions();
                    if(index<1 || index>GameSettingsService.usedCount(game,questions.size())) throw LessonFailure.conflict("QUESTION_NOT_FOUND");

                } else if(index!=1) throw LessonFailure.conflict("QUESTION_NOT_FOUND");
                var now=clock.sample();long token=old.map(w->w.token()+1).orElse(1L);
                active=PhaseWindow.open(index,vn.edu.multigame.game.enums.Phase.QUESTION_OPEN,token,now.monotonicMs(),now.epochMs(),objective(game.mode())?game.durationMs():payload.path("durationMs").longValue());
                realtime.open(id,game.id(),position,token,index,active.openedEpochMs(),active.deadlineEpochMs());if(game.mode()==vn.edu.multigame.lesson.domain.LessonMode.ASSIGNMENT)assignments.deadline(game.id(),active.deadlineEpochMs());store.bump(id);
            }
            case "CLOSE_LESSON_WINDOW","CANCEL_LESSON_WINDOW" -> {
                var window=realtime.window(id).orElseThrow(()->LessonFailure.conflict("WINDOW_NOT_OPEN"));
                if(store.games(id,0).stream().anyMatch(g->g.id()==window.gameId() && objective(g.mode())))throw LessonFailure.conflict("AUTOMATIC_GAME_CONTROL");
                if(window.token()!=payload.path("token").longValue()) throw LessonFailure.conflict("STALE_WINDOW");
                if(realtime.close(id,window.token(),type.startsWith("CANCEL")?"CANCELLED":"CLOSED")) {store.bump(id);}
            }
            case "SUBMIT_LESSON_ACTION" -> {
                var window=realtime.window(id).orElseThrow(()->LessonFailure.conflict("WINDOW_NOT_OPEN"));
                if(!window.status().equals("OPEN") || phase==null || window.token()!=payload.path("token").longValue() || !phase.accepts(ingress.receivedAtMs())) throw LessonFailure.conflict("WINDOW_CLOSED");
                var game=before.lesson().games().stream().filter(g->g.id()==window.gameId()).findFirst().orElseThrow(()->new LessonFailure(org.springframework.http.HttpStatus.NOT_FOUND,"GAME_NOT_FOUND"));
                if(!game.status().equals("RUNNING") || !game.participating()) throw LessonFailure.conflict("GAME_PARTICIPATION_REQUIRED");
                if(game.mode()==vn.edu.multigame.lesson.domain.LessonMode.LUCKY_WHEEL) throw LessonFailure.conflict("UNSUPPORTED_ACTION");
                boolean multiple=game.mode()==vn.edu.multigame.lesson.domain.LessonMode.ASSIGNMENT;
                if(!multiple && realtime.submitted(game.id(),window.token(),user.getId())) throw LessonFailure.conflict("ALREADY_SUBMITTED");
                if(objective(game.mode())) {
                    String value=payload.path("value").textValue();
                    if(game.mode()==vn.edu.multigame.lesson.domain.LessonMode.QUIZ && !value.matches("[ABCD]"))throw LessonFailure.conflict("INVALID_QUIZ_OPTION");
                    if(quiz.answered(game.id(),window.questionIndex(),user.getId()))throw LessonFailure.conflict("ALREADY_SUBMITTED");
                    var questions=content.get(game.id()).orElseThrow().questions();
                    var question=questions.get(window.questionIndex()-1);
                    boolean correct=game.mode()==vn.edu.multigame.lesson.domain.LessonMode.QUIZ?question.correctAnswer().name().equals(value):FillBlankMatcher.matches(value,question.acceptedAnswers(),question.matchingPolicy());
                    quiz.answer(game.id(),window.questionIndex(),user.getId(),value,correct,ingress.serverTimeMs());
                    quiz.score(id,game.id(),user.getId(),QuizScoring.score(quiz.correct(game.id(),user.getId()),GameSettingsService.usedCount(game,questions.size()),game.budget()));store.total(id,user.getId());store.bump(id);
                }
                realtime.input(window,user.getId(),command.requestId(),multiple?command.requestId():Long.toString(user.getId()),payload.path("value").textValue(),ingress.serverTimeMs(),ingress.sequence());
            }
            case "SUBSCRIBE_LESSON","RECONNECT_LESSON","UNSUBSCRIBE_LESSON" -> { }
            default -> throw LessonFailure.conflict("UNSUPPORTED_ACTION");
        }
        realtime.receipt(user.getId(),command,hash,ingress.sequence(),ingress.serverTimeMs());
        return new Result(snapshot(caller,id),ingress.sequence(),ingress.serverTimeMs(),false,active);
    }
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.room.repository.ClassRoomStore memberships;
    private LessonView.Game assignmentGame(long lesson,int position){return store.games(lesson,0).stream().filter(g->g.position()==position && g.mode()==vn.edu.multigame.lesson.domain.LessonMode.ASSIGNMENT).findFirst().orElseThrow(()->LessonFailure.conflict("ASSIGNMENT_NOT_FOUND"));}
    private void runningAssignment(LessonRealtimeView before,LessonView.Game game){if(!before.lesson().status().equals("RUNNING") || !game.status().equals("RUNNING"))throw LessonFailure.conflict("GAME_NOT_RUNNING");}
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.achievements.service.LearningAwardService learningAwards;
    private static boolean objective(vn.edu.multigame.lesson.domain.LessonMode mode){return mode==vn.edu.multigame.lesson.domain.LessonMode.QUIZ || mode==vn.edu.multigame.lesson.domain.LessonMode.FILL_BLANK;}
    private void finishQuestions(LessonRealtimeStore.Window window,java.util.Set<Long> locked) {
        var game=store.games(window.lessonId(),0).stream().filter(g->g.id()==window.gameId()).findFirst().orElseThrow();
        if(!objective(game.mode()) || !game.status().equals("RUNNING"))return;
        if(window.questionIndex()!=questionCount(game))return;
        if(!locked.containsAll(store.participants(game.id())))throw LessonFailure.conflict("PARTICIPANTS_CHANGED_RETRY");
        quiz.absent(game.id());for(long student:store.participants(game.id()))store.total(window.lessonId(),student);
        store.gameStatus(game.id(),"COMPLETED");store.finishIfReady(window.lessonId());events.publishEvent(new LessonChanged(window.lessonId()));
        if(game.mode()==vn.edu.multigame.lesson.domain.LessonMode.QUIZ)learningAwards.quizCompleted(game.id(),store.lesson(window.lessonId(),false).courseClassId(),questionCount(game),store.participants(game.id()),clock.sample().epochMs());
    }
    /** Returns the next PhaseWindow if a next question was opened, or null if game ended. */
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public PhaseWindow expire(long lesson,long token) {
        var locked=new java.util.TreeSet<Long>();realtime.window(lesson).ifPresent(w->locked.addAll(store.participants(w.gameId())));for(long account:locked)accounts.lock(account);
        store.lesson(lesson,true);var window=realtime.window(lesson).orElse(null);
        if(window==null || window.token()!=token || !window.status().equals("OPEN")) return null;
        if(!realtime.close(lesson,token,"CLOSED")) return null;
        store.bump(lesson);
        var game=store.games(window.lessonId(),0).stream().filter(g->g.id()==window.gameId()).findFirst().orElse(null);
        if(game==null || !objective(game.mode()) || !game.status().equals("RUNNING")) return null;
        int totalQuestions=questionCount(game);
        int nextIndex=window.questionIndex()+1;
        if(nextIndex>totalQuestions) {
            // Last question finished – score, complete and close.
            if(!locked.containsAll(store.participants(game.id())))throw LessonFailure.conflict("PARTICIPANTS_CHANGED_RETRY");
            quiz.absent(game.id());for(long student:store.participants(game.id()))store.total(window.lessonId(),student);
            store.gameStatus(game.id(),"COMPLETED");store.finishIfReady(window.lessonId());events.publishEvent(new LessonChanged(window.lessonId()));
            if(game.mode()==vn.edu.multigame.lesson.domain.LessonMode.QUIZ)learningAwards.quizCompleted(game.id(),store.lesson(window.lessonId(),false).courseClassId(),totalQuestions,store.participants(game.id()),clock.sample().epochMs());
            return null;
        }
        // Open the next question automatically.
        return openWindowAt(lesson,game,window.position(),nextIndex,window.token());
    }

    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public PhaseWindow resumeObjective(long lesson) {
        var candidate=store.games(lesson,0).stream().filter(g->objective(g.mode()) && g.status().equals("RUNNING")).findFirst();
        if(candidate.isEmpty())return null;
        var locked=new java.util.TreeSet<>(store.participants(candidate.get().id()));for(long account:locked)accounts.lock(account);
        store.lesson(lesson,true);
        var game=store.games(lesson,0).stream().filter(g->g.id()==candidate.get().id() && g.status().equals("RUNNING")).findFirst().orElse(null);
        if(game==null)return null;
        var existing=realtime.window(lesson);
        if(existing.isPresent() && existing.get().status().equals("OPEN"))return null;
        if(quiz.opened(game.id())==0)return openFirstWindow(lesson,game.id(),game.position());
        // Older manual-control versions may have stopped between questions. Continue from durable progress.
        if(existing.isPresent() && existing.get().gameId()==game.id()) {
            var window=existing.get();
            if(window.questionIndex()<questionCount(game))return openWindowAt(lesson,game,game.position(),window.questionIndex()+1,window.token());
            finishQuestions(window,locked);
        }
        return null;
    }

    /** Opens question at given index; called from startGame (index=1) or expire (index=next). */
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public PhaseWindow openFirstWindow(long lesson,long gameId,int position) {
        var locked=new java.util.TreeSet<Long>();locked.addAll(store.participants(gameId));for(long account:locked)accounts.lock(account);
        store.lesson(lesson,true);
        var game=store.games(lesson,0).stream().filter(g->g.id()==gameId).findFirst().orElseThrow();
        if(!objective(game.mode()) || !game.status().equals("RUNNING")) return null;
        // Only open if no window is already open for this game.
        var existing=realtime.window(lesson);
        if(existing.isPresent() && existing.get().gameId()==gameId) return null;
        quiz.open(game.id(),1);
        if(game.mode()==vn.edu.multigame.lesson.domain.LessonMode.QUIZ)learningAwards.captureInitialPlayers(game.id());
        long existingToken=existing.map(w->w.token()).orElse(0L);
        return openWindowAt(lesson,game,position,1,existingToken);
    }

    private PhaseWindow openWindowAt(long lesson,LessonView.Game game,int position,int questionIndex,long previousToken) {
        var now=clock.sample();long token=previousToken+1;
        if(questionIndex>1) quiz.open(game.id(),questionIndex);
        var active=PhaseWindow.open(questionIndex,vn.edu.multigame.game.enums.Phase.QUESTION_OPEN,token,now.monotonicMs(),now.epochMs(),game.durationMs());
        realtime.open(lesson,game.id(),position,token,questionIndex,active.openedEpochMs(),active.deadlineEpochMs());
        store.bump(lesson);
        return active;
    }
}
