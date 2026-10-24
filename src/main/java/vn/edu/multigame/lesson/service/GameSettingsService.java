package vn.edu.multigame.lesson.service;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.lesson.repository.*;
import vn.edu.multigame.lesson.domain.LessonMode;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.user.enums.UserRole;

@Service @Profile("mysql")
public class GameSettingsService {
    public record Settings(long revision,BigDecimal budget,Integer durationMs,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=QuestionCountDeserializer.class) Integer questionCount,List<String> wheelCells,Integer selectedCount) {}
    public static final class QuestionCountDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
        @Override public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser,com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            if(parser.currentToken()!=com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)throw com.fasterxml.jackson.databind.JsonMappingException.from(parser,"questionCount must be an integer");
            return parser.getIntValue();
        }
    }
    private final LessonStore lessons;
    private final GameContentStore content;
    private final WheelStore wheels;
    private final AuthService auth;
    private final AccountAccessPolicy accounts;
    private final ClassRoomStore rooms;
    private final ApplicationEventPublisher events;
    public GameSettingsService(LessonStore lessons,GameContentStore content,WheelStore wheels,AuthService auth,AccountAccessPolicy accounts,ClassRoomStore rooms,ApplicationEventPublisher events){this.lessons=lessons;this.content=content;this.wheels=wheels;this.auth=auth;this.accounts=accounts;this.rooms=rooms;this.events=events;}
    private LessonView.Game authorize(Authentication caller,long id,int position,long revision){
        var user=auth.requireActiveUser(caller);accounts.lock(user.getId());auth.requireActiveUser(caller);
        var lesson=lessons.lesson(id,true);
        if(user.getRole()!=UserRole.GIANG_VIEN || !rooms.assigned(lesson.courseClassId(),user.getId()))throw new LessonFailure(org.springframework.http.HttpStatus.FORBIDDEN,"CLASS_NOT_ASSIGNED");
        if(lesson.revision()!=revision)throw LessonFailure.conflict("STALE_REVISION");
        return lessons.games(id,0).stream().filter(g->g.position()==position).findFirst().orElseThrow(()->new LessonFailure(org.springframework.http.HttpStatus.NOT_FOUND,"GAME_NOT_FOUND"));
    }
    private static BigDecimal amount(BigDecimal value){
        if(value==null || value.signum()<0 || value.scale()>2 || value.compareTo(new BigDecimal("100"))>0)throw LessonFailure.conflict("INVALID_BUDGET");
        return value;
    }
    private void budget(long lesson,long game,BigDecimal value){
        amount(value);
        if(lessons.games(lesson,0).stream().filter(g->g.id()!=game).map(LessonView.Game::budget).reduce(value,BigDecimal::add).compareTo(new BigDecimal("100"))>0)throw LessonFailure.conflict("LESSON_BUDGET_EXCEEDED");
    }
    public static boolean objective(LessonMode mode){return mode==LessonMode.QUIZ || mode==LessonMode.FILL_BLANK;}
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public void configure(Authentication caller,long lesson,int position,Settings input){
        var game=authorize(caller,lesson,position,input.revision());
        if(!game.status().equals("READY"))throw LessonFailure.conflict("GAME_CONFIG_FROZEN");
        int duration=game.durationMs(),count=game.selectedCount();Integer questionCount=game.questionCount();BigDecimal max;
        List<String> cells=game.wheelCells();
        if(objective(game.mode())){
            if(input.durationMs()==null || input.durationMs()<1000)throw LessonFailure.conflict("INVALID_GAME_DURATION");
            duration=input.durationMs();
            int questions=content.get(game.id()).map(c->c.questions().size()).orElse(0);
            if(questions==0)throw LessonFailure.conflict("GAME_CONTENT_REQUIRED");
            if(input.questionCount()==null || input.questionCount()<1 || input.questionCount()>questions)throw LessonFailure.conflict("INVALID_QUESTION_COUNT");
            questionCount=input.questionCount();max=amount(input.budget());
        }else{
            max=amount(input.budget());
            if(game.mode()==LessonMode.LUCKY_WHEEL){
                cells=input.wheelCells();
                if(cells==null || cells.isEmpty() || cells.size()>100 || cells.stream().anyMatch(c->c==null || c.isBlank() || c.length()>500))throw LessonFailure.conflict("INVALID_WHEEL_CELLS");
                if(input.selectedCount()==null || input.selectedCount()<1 || input.selectedCount()>10000)throw LessonFailure.conflict("INVALID_WHEEL_K");
                count=input.selectedCount();
            }
        }
        budget(lesson,game.id(),max);lessons.configure(game.id(),max,duration,questionCount,cells,count);lessons.bump(lesson);events.publishEvent(new LessonChanged(lesson));
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public void visibility(Authentication caller,long lesson,int position,LessonRequests.Visibility input){
        var game=authorize(caller,lesson,position,input.revision());lessons.gameVisibility(game.id(),input.visible());lessons.bump(lesson);events.publishEvent(new LessonChanged(lesson));
    }
    public void initializeWheel(long course,LessonView.Game game){
        if(game.mode()!=LessonMode.LUCKY_WHEEL || game.wheelCells()!=null)return;
        var cells=new ArrayList<String>();wheels.config(course).path("cells").forEach(cell->cells.add(cell.asText()));
        lessons.configure(game.id(),game.budget(),game.durationMs(),null,cells,game.selectedCount());
    }
    /** Preserve old scores; initialize common settings only for games which have not started. */
    public void contentChanged(long lesson,LessonView.Game game,int count){
        if(!objective(game.mode()) || count==0)return;
        int used=game.questionCount()==null?count:Math.min(game.questionCount(),count);
        lessons.configure(game.id(),game.budget(),game.durationMs(),used,game.wheelCells(),game.selectedCount());
    }
    public static int usedCount(LessonView.Game game,int available){
        return objective(game.mode()) && game.questionCount()!=null?game.questionCount():available;
    }
}
