package vn.edu.multigame.lesson.service;

import java.math.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.user.enums.UserRole;
import vn.edu.multigame.room.service.ClassMembershipService;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.lesson.domain.*;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.lesson.repository.LessonStore;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;

@Service @Profile("mysql") @Transactional(readOnly=true)
public class LessonService {
    private final org.springframework.context.ApplicationEventPublisher events;
    private final LessonStore store;
    private final vn.edu.multigame.lesson.repository.LessonRealtimeStore realtime;
    private final AuthService auth;
    private final AccountAccessPolicy accounts;
    private final ClassRoomStore rooms;
    private final ClassMembershipService memberships;
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.achievements.service.LearningAwardService awards;
    private final CommandFingerprint fingerprints;
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.questionbank.service.QuestionBankService banks;
    public LessonService(LessonStore store,AuthService auth,AccountAccessPolicy accounts,ClassRoomStore rooms,ClassMembershipService memberships,CommandFingerprint fingerprints,org.springframework.context.ApplicationEventPublisher events,vn.edu.multigame.lesson.repository.LessonRealtimeStore realtime) {
        this.realtime=realtime;this.events=events;this.store=store;this.auth=auth;this.accounts=accounts;this.rooms=rooms;this.memberships=memberships;this.fingerprints=fingerprints;
    }
    private LessonView changed(LessonView view) {events.publishEvent(new LessonChanged(view.id()));return view;}
    private long user(Authentication caller) {return auth.requireActiveUser(caller).getId();}
    private void lockCaller(Authentication caller) {long id=user(caller);accounts.lock(id);auth.requireActiveUser(caller);}
    private void teacher(Authentication caller,long classId) {
        var user=auth.requireActiveUser(caller);
        if(user.getRole()!=UserRole.GIANG_VIEN || !rooms.assigned(classId,user.getId())) throw new LessonFailure(HttpStatus.FORBIDDEN,"CLASS_NOT_ASSIGNED");
    }
    private void student(Authentication caller,long classId) {memberships.requireApproved(caller,classId);}
    private static void draft(LessonState lesson) {if(!lesson.status().equals("DRAFT")) throw LessonFailure.conflict("LESSON_CONFIG_FROZEN");}
    private static void revision(LessonState lesson,long expected) {if(lesson.revision()!=expected) throw LessonFailure.conflict("STALE_REVISION");}
    public static void validatePlan(LessonRequests.Plan plan) {
        if(plan.slots()==null || plan.slots().size()!=3 || plan.slots().stream().anyMatch(Objects::isNull)
                || plan.slots().getFirst().mode()!=LessonMode.LUCKY_WHEEL
                || plan.slots().stream().map(LessonRequests.Slot::mode).anyMatch(Objects::isNull)
                || plan.slots().stream().map(LessonRequests.Slot::mode).distinct().count()!=3) throw LessonFailure.conflict("INVALID_THREE_SLOTS");
        BigDecimal total=BigDecimal.ZERO;
        for(var slot:plan.slots()) {
            if(slot.budget()==null || slot.budget().signum()<0 || slot.budget().scale()>2 || slot.budget().compareTo(new BigDecimal("100"))>0) throw LessonFailure.conflict("INVALID_BUDGET");
            total=total.add(slot.budget());
        }
        if(total.compareTo(new BigDecimal("100"))>0) throw LessonFailure.conflict("LESSON_BUDGET_EXCEEDED");
    }
    private LessonView view(LessonState lesson,long student) {
        return new LessonView(lesson.id(),lesson.courseClassId(),lesson.number(),lesson.title(),lesson.visible(),lesson.status(),lesson.revision(),store.games(lesson.id(),student).stream().filter(g->student==0 || g.visible()).toList(),
                store.attending(lesson.id(),student),store.scoreTotal(lesson.id(),student));
    }
    public List<LessonView> list(Authentication caller,long classId,boolean teacher) {
        if(teacher) teacher(caller,classId); else student(caller,classId);
        long id=teacher?0:user(caller);return store.list(classId,teacher).stream().map(l->view(l,id)).toList();
    }
    public LessonView get(Authentication caller,long id,boolean teacher) {
        var lesson=store.lesson(id,false);
        if(teacher) teacher(caller,lesson.courseClassId());
        else {student(caller,lesson.courseClassId());if(!lesson.visible()) throw new LessonFailure(HttpStatus.NOT_FOUND,"LESSON_NOT_FOUND");}
        return view(lesson,teacher?0:user(caller));
    }
    public LessonView.Progress progress(Authentication caller,long classId) {teacher(caller,classId);return store.progress(classId);}
    public List<LessonRealtimeView.Participant> participants(Authentication caller,long id) {
        var parent=store.lesson(id,false);teacher(caller,parent.courseClassId());var room=rooms.forClass(parent.courseClassId()).orElseThrow();
        return rooms.roster(room.id()).stream().map(member->{
            var games=store.games(id,member.studentId());var scores=new HashMap<Integer,BigDecimal>();var correct=new HashMap<Integer,Integer>();
            for(var game:games){scores.put(game.position(),game.score());if(game.mode()==LessonMode.QUIZ || game.mode()==LessonMode.FILL_BLANK)correct.put(game.position(),store.correct(game.id(),member.studentId()));}
            boolean eligible=member.status().equals("APPROVED") && !rooms.excluded(member.studentId(),room) && !accounts.state(member.studentId()).banned(System.currentTimeMillis());
            return new LessonRealtimeView.Participant(member.studentId(),member.hoTen(),member.maSinhVien(),store.attending(id,member.studentId()),eligible,games.stream().filter(LessonView.Game::participating).map(LessonView.Game::position).toList(),scores,correct);
        }).toList();
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public LessonView create(Authentication caller,long classId,LessonRequests.Plan input) {
        lockCaller(caller);teacher(caller,classId);validatePlan(input);int quota=store.lockClass(classId);
        String hash=fingerprints.of("CREATE_LESSON","CLASS",classId,null,input);
        var prior=store.prior(classId,input.requestId());
        if(prior.isPresent()) {if(store.deleted(prior.get().id()))throw LessonFailure.conflict("LESSON_DELETED");if(!prior.get().createFingerprint().equals(hash)) throw LessonFailure.conflict("REQUEST_ID_REUSED");return changed(view(prior.get(),0));}
        int created=store.progress(classId).created();if(created>=quota) throw LessonFailure.conflict("LESSON_LIMIT");
        if(input.sourceLessonId()!=null) template(caller,classId,input.sourceLessonId());
        long id=store.create(classId,created+1,input,hash);
        if(input.sourceLessonId()!=null) {
            var source=store.games(input.sourceLessonId(),0);var target=store.games(id,0);
            for(int i=0;i<3;i++) {
                var from=source.get(i);var to=target.get(i);
                if(from.mode()!=to.mode())continue;
                store.configure(to.id(),to.budget(),from.durationMs(),from.questionCount(),from.wheelCells(),from.selectedCount());
                store.gameVisibility(to.id(),from.visible());
                content.get(from.id()).ifPresent(c->content.select(to.id(),c.sourceQuizId(),c.sourceRevision(),c.mode(),c.title(),c.questions()));
            }
        }
        if(input.gameSettings()!=null)for(var configuration:input.gameSettings()) {
            var configured=game(id,configuration.position());
            settings.configure(caller,id,configured.position(),new GameSettingsService.Settings(store.lesson(id,false).revision(),configured.budget(),configuration.durationMs(),configuration.questionCount(),configuration.wheelCells(),configuration.selectedCount()));
            if(configuration.assignmentDeadlineAtMs()!=null && configured.mode()==LessonMode.ASSIGNMENT) {
                if(configuration.assignmentDeadlineAtMs()<=clock.sample().epochMs())throw LessonFailure.conflict("INVALID_DEADLINE");
                assignments.deadline(configured.id(),configuration.assignmentDeadlineAtMs());
            }
        }
        return changed(view(store.lesson(id,false),0));
    }
    public record Template(LessonView lesson,Map<Integer,GameContentView> contents) {}
    public List<LessonView> templates(Authentication caller,long classId) {
        teacher(caller,classId);
        return store.templates(classId,user(caller)).stream()
            .filter(l->store.games(l.id(),0).stream().allMatch(g->content.get(g.id()).map(c->banks.canReuse(caller,c.sourceQuizId())).orElse(true)))
            .map(l->view(l,0)).toList();
    }
    @Transactional
    public Template template(Authentication caller,long classId,long source) {
        teacher(caller,classId);var original=store.lesson(source,false);teacher(caller,original.courseClassId());
        if(!original.status().equals("COMPLETED"))throw LessonFailure.conflict("TEMPLATE_NOT_COMPLETED");
        if(rooms.forClass(classId).orElseThrow().subjectId()!=rooms.forClass(original.courseClassId()).orElseThrow().subjectId())throw LessonFailure.conflict("TEMPLATE_SUBJECT_MISMATCH");
        var contents=new LinkedHashMap<Integer,GameContentView>();
        for(var game:store.games(source,0))content.get(game.id()).ifPresent(c->{banks.previewLocked(caller,c.sourceQuizId());contents.put(game.position(),c);});
        return new Template(view(original,0),contents);
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public void delete(Authentication caller,long id,long expected) {
        lockCaller(caller);var initial=store.lesson(id,false);teacher(caller,initial.courseClassId());
        store.lockClass(initial.courseClassId());var lesson=store.lesson(id,true);revision(lesson,expected);
        if(lesson.status().equals("COMPLETED"))throw LessonFailure.conflict("COMPLETED_LESSON_CANNOT_DELETE");
        store.delete(id,lesson.courseClassId());
        events.publishEvent(new LessonDeleted(id,lesson.courseClassId()));
        events.publishEvent(new vn.edu.multigame.leaderboard.service.LearningScoreChanged(lesson.courseClassId()));
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public LessonView edit(Authentication caller,long id,LessonRequests.Edit input) {
        lockCaller(caller);var lesson=store.lesson(id,true);teacher(caller,lesson.courseClassId());draft(lesson);revision(lesson,input.revision());validatePlan(input.plan());
        store.edit(id,input.plan());return changed(view(store.lesson(id,false),0));
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public LessonView visible(Authentication caller,long id,LessonRequests.Visibility input) {
        lockCaller(caller);var lesson=store.lesson(id,true);teacher(caller,lesson.courseClassId());revision(lesson,input.revision());
        if(lesson.visible()!=input.visible()) store.visibility(id,input.visible());return changed(view(store.lesson(id,false),0));
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public LessonView start(Authentication caller,long id,long expected) {
        lockCaller(caller);var lesson=store.lesson(id,true);teacher(caller,lesson.courseClassId());
        if(lesson.status().equals("RUNNING")) return changed(view(lesson,0));
        draft(lesson);revision(lesson,expected);if(!lesson.visible()) throw LessonFailure.conflict("LESSON_HIDDEN");
        store.start(id);return changed(view(store.lesson(id,false),0));
    }
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.lesson.repository.GameContentStore content;
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.lesson.repository.AssignmentStore assignments;
    @org.springframework.beans.factory.annotation.Autowired private vn.edu.multigame.realtime.timer.ServerClock clock;
    @org.springframework.beans.factory.annotation.Autowired private GameSettingsService settings;
    private boolean contentMissing(long game){return content.get(game).map(c->c.questions().isEmpty()).orElse(true);}
    private LessonView.Game game(long lesson,int position) {return store.games(lesson,0).stream().filter(g->g.position()==position).findFirst().orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"GAME_NOT_FOUND"));}
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public LessonView startGame(Authentication caller,long id,int position,long expected) {
        lockCaller(caller);var lesson=store.lesson(id,true);teacher(caller,lesson.courseClassId());var game=game(id,position);
        if(!game.status().equals("READY")) return changed(view(lesson,0));
        revision(lesson,expected);if(!lesson.status().equals("RUNNING")) throw LessonFailure.conflict("LESSON_NOT_RUNNING");
        if(store.games(id,0).stream().anyMatch(g->g.position()<position && !g.status().equals("COMPLETED"))) throw LessonFailure.conflict("PREVIOUS_GAME_INCOMPLETE");
        if(game.mode()!=LessonMode.LUCKY_WHEEL && contentMissing(game.id()))throw LessonFailure.conflict("GAME_CONTENT_REQUIRED");
        settings.initializeWheel(lesson.courseClassId(),game);
        content.get(game.id()).ifPresent(c->settings.contentChanged(id,game,c.questions().size()));
        store.gameStatus(game.id(),"RUNNING");
        if(game.mode()==LessonMode.ASSIGNMENT)assignments.deadline(game.id()).ifPresent(deadline->{long now=clock.sample().epochMs();long token=realtime.window(id).map(w->w.token()+1).orElse(1L);realtime.open(id,game.id(),position,token,1,Math.min(now,deadline-1),deadline);if(deadline<=now)realtime.close(id,token,"CLOSED");});
        store.bump(id);return changed(view(store.lesson(id,false),0));
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public LessonView attend(Authentication caller,long id,Integer position) {
        lockCaller(caller);var lesson=store.lesson(id,true);student(caller,lesson.courseClassId());
        if(!lesson.visible()) throw new LessonFailure(HttpStatus.NOT_FOUND,"LESSON_NOT_FOUND");
        if(!lesson.status().equals("RUNNING")) throw LessonFailure.conflict("LESSON_NOT_RUNNING");
        long student=user(caller);
        if(position==null) store.attend(id,student,System.currentTimeMillis());
        else {
            var game=game(id,position);
            if(!game.visible())throw new LessonFailure(HttpStatus.NOT_FOUND,"GAME_NOT_FOUND");
            if(!store.attending(id,student)) throw LessonFailure.conflict("LESSON_ATTENDANCE_REQUIRED");
            if(!game.status().equals("RUNNING")) throw LessonFailure.conflict("GAME_NOT_RUNNING");
            store.participate(game.id(),student,System.currentTimeMillis());
            if(game.mode()==LessonMode.QUIZ)realtime.window(id).filter(w->w.gameId()==game.id() && w.questionIndex()==1 && w.status().equals("OPEN") && clock.sample().epochMs()<w.deadline()).ifPresent(w->awards.joinedFirstQuestion(game.id(),student));
        }
        return changed(view(store.lesson(id,false),student));
    }
    /** Trusted server integration point for TASK 07–10; deliberately absent from HTTP controllers. */
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public LessonView completeGame(Authentication caller,long id,int position,Map<Long,BigDecimal> results) {
        var initial=game(id,position);var locked=new TreeSet<>(store.participants(initial.id()));locked.add(user(caller));
        for(long account:locked) accounts.lock(account);
        var lesson=store.lesson(id,true);teacher(caller,lesson.courseClassId());var game=game(id,position);
        if(game.status().equals("COMPLETED")) return changed(view(lesson,0));
        if(!game.status().equals("RUNNING")) throw LessonFailure.conflict("GAME_NOT_RUNNING");
        var participants=store.participants(game.id());
        if(!locked.containsAll(participants)) throw LessonFailure.conflict("PARTICIPANTS_CHANGED_RETRY");
        if(!participants.containsAll(results.keySet())) throw LessonFailure.conflict("SCORE_FOR_NON_PARTICIPANT");
        var room=rooms.forClass(lesson.courseClassId()).orElseThrow();
        for(long student:participants) {
            BigDecimal score=results.getOrDefault(student,BigDecimal.ZERO);
            if(score==null || score.signum()<0 || score.compareTo(game.budget())>0 || score.scale()>2) throw LessonFailure.conflict("INVALID_GAME_SCORE");
            if(rooms.excluded(student,room) || accounts.state(student).banned(System.currentTimeMillis())) score=BigDecimal.ZERO;
            store.score(game.id(),student,score.setScale(2,RoundingMode.HALF_UP));store.total(id,student);
        }
        realtime.window(id).filter(w->w.gameId()==game.id()).ifPresent(w->realtime.close(id,w.token(),"CLOSED"));
        store.gameStatus(game.id(),"COMPLETED");store.finishIfReady(id);return changed(view(store.lesson(id,false),0));
    }
}
