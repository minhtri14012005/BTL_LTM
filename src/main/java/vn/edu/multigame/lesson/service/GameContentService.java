package vn.edu.multigame.lesson.service;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.user.enums.UserRole;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.lesson.repository.*;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.questionbank.enums.GameMode;
import vn.edu.multigame.questionbank.service.QuestionBankService;

@Service @Profile("mysql") @Transactional(readOnly=true)
public class GameContentService {
    private final org.springframework.context.ApplicationEventPublisher events;
    @org.springframework.beans.factory.annotation.Autowired private GameSettingsService settings;
    private final LessonStore lessons;
    private final GameContentStore contents;
    private final AuthService auth;
    private final AccountAccessPolicy accounts;
    private final ClassRoomStore rooms;
    private final QuestionBankService banks;
    public GameContentService(LessonStore lessons,GameContentStore contents,AuthService auth,AccountAccessPolicy accounts,ClassRoomStore rooms,QuestionBankService banks,org.springframework.context.ApplicationEventPublisher events) {
        this.events=events;this.lessons=lessons;this.contents=contents;this.auth=auth;this.accounts=accounts;this.rooms=rooms;this.banks=banks;
    }
    private LessonView.Game authorize(Authentication caller,long lesson,int position,boolean lock) {
        var user=auth.requireActiveUser(caller);
        if(user.getRole()!=UserRole.GIANG_VIEN) throw new LessonFailure(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(lock) {accounts.lock(user.getId());auth.requireActiveUser(caller);}
        var parent=lessons.lesson(lesson,lock);
        if(!rooms.assigned(parent.courseClassId(),user.getId())) throw new LessonFailure(HttpStatus.FORBIDDEN,"CLASS_NOT_ASSIGNED");
        var game=lessons.games(lesson,0).stream().filter(g->g.position()==position).findFirst().orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"GAME_NOT_FOUND"));
        if(lock && !game.status().equals("READY")) throw LessonFailure.conflict("CONTENT_FROZEN");
        return game;
    }
    public GameContentView get(Authentication caller,long lesson,int position) {
        var game=authorize(caller,lesson,position,false);
        return contents.get(game.id()).orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND"));
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public GameContentView select(Authentication caller,long lesson,int position,GameContentRequests.Select input) {
        var game=authorize(caller,lesson,position,true);
        if(lessons.lesson(lesson,false).revision()!=input.lessonRevision()) throw LessonFailure.conflict("STALE_REVISION");
        var source=banks.previewLocked(caller,input.quizId());
        if(!source.mode().name().equals(game.mode().name())) throw LessonFailure.conflict("CONTENT_MODE_MISMATCH");
        if(source.subjectId()!=rooms.forClass(lessons.lesson(lesson,false).courseClassId()).orElseThrow().subjectId())throw LessonFailure.conflict("CONTENT_SUBJECT_MISMATCH");
        contents.select(game.id(),source.id(),source.revision(),source.mode(),source.title(),source.questions(),source.subjectId());settings.contentChanged(lesson,game,source.questions().size());lessons.bump(lesson);events.publishEvent(new LessonChanged(lesson));
        return get(caller,lesson,position);
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public GameContentView edit(Authentication caller,long lesson,int position,GameContentRequests.Edit input) {
        var game=authorize(caller,lesson,position,true);var existing=contents.get(game.id()).orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND"));
        if(existing.revision()!=input.revision()) throw LessonFailure.conflict("STALE_REVISION");
        var questions=banks.snapshotQuestions(GameMode.valueOf(game.mode().name()),input.questions());
        contents.edit(game.id(),input.title().strip(),questions);settings.contentChanged(lesson,game,questions.size());lessons.bump(lesson);events.publishEvent(new LessonChanged(lesson));return get(caller,lesson,position);
    }
}
