package vn.edu.multigame.lesson.service;
import java.util.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.multigame.lesson.repository.*;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;
@Service @Profile("mysql") @Transactional(readOnly=true)
public class WheelService {
 private final WheelStore wheel; private final LessonStore lessons; private final LessonRealtimeStore realtime;
 private final AccountAccessPolicy accounts;private final AuthService auth;private final ClassRoomStore rooms;
 private final CommandFingerprint hashes;private final ApplicationEventPublisher events;
 private final ObjectMapper json;private final SecureRandom random=new SecureRandom();
 public WheelService(WheelStore wheel,LessonStore lessons,LessonRealtimeStore realtime,AccountAccessPolicy accounts,AuthService auth,ClassRoomStore rooms,CommandFingerprint hashes,ApplicationEventPublisher events,ObjectMapper json){this.wheel=wheel;this.lessons=lessons;this.realtime=realtime;this.accounts=accounts;this.auth=auth;this.rooms=rooms;this.hashes=hashes;this.events=events;this.json=json;}
 private void require(boolean condition,String code){if(!condition)throw LessonFailure.conflict(code);}
 private long teacher(Authentication caller,long course){var u=auth.requireActiveUser(caller);if(u.getRole()!=vn.edu.multigame.user.enums.UserRole.GIANG_VIEN || !rooms.assigned(course,u.getId()))throw new LessonFailure(org.springframework.http.HttpStatus.FORBIDDEN,"CLASS_NOT_ASSIGNED");return u.getId();}
 public JsonNode config(Authentication caller,long course){teacher(caller,course);return wheel.config(course);}
 @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
 public JsonNode configure(Authentication caller,long course,long revision,List<String> cells){long actor=teacher(caller,course);accounts.lock(actor);teacher(caller,course);lessons.lockClass(course);require(wheel.config(course).path("revision").asLong()==revision,"STALE_REVISION");require(cells!=null && cells.size()<=100 && cells.stream().allMatch(s->s!=null && !s.isBlank() && s.length()<=500),"INVALID_WHEEL_CELLS");wheel.config(course,json.valueToTree(cells));return wheel.config(course);}
 private boolean eligible(long student,long course){var room=rooms.forClass(course).orElseThrow();return !accounts.state(student).banned(System.currentTimeMillis()) && !rooms.excluded(student,room) && rooms.member(room.id(),student).map(m->m.status().equals("APPROVED")).orElse(false);}
 @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
 public JsonNode act(Authentication caller,long lesson,String action,String request,long revision,List<Long> pool,Integer k,Map<Long,BigDecimal> grades){
  require(request!=null && request.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),"INVALID_REQUEST_ID");
  var initial=lessons.lesson(lesson,false);long actor=teacher(caller,initial.courseClassId());
  var game=lessons.games(lesson,0).getFirst();var participants=new TreeSet<>(lessons.participants(game.id()));var locked=new TreeSet<>(participants);locked.add(actor);for(long id:locked)accounts.lock(id);
  var parent=lessons.lesson(lesson,true);teacher(caller,parent.courseClassId());require(participants.containsAll(lessons.participants(game.id())),"PARTICIPANTS_CHANGED_RETRY");
  String hash=hashes.of(action,"WHEEL",lesson,null,Arrays.asList(revision,pool,k,grades));String prior=wheel.receipt(actor,request);
  if(prior!=null){require(prior.equals(hash),"REQUEST_ID_REUSED");return wheel.round(lesson);}
  var old=wheel.round(lesson);require((old==null?0:old.path("revision").asLong())==revision,"STALE_REVISION");
  require(parent.status().equals("RUNNING") && game.status().equals("RUNNING"),"GAME_NOT_RUNNING");
  ObjectNode state=old==null?json.createObjectNode():(ObjectNode)old.deepCopy();
  switch(action){
   case "select" -> {
    require(old==null,"WHEEL_ALREADY_SELECTED");require(pool!=null && !pool.isEmpty() && pool.stream().allMatch(Objects::nonNull) && new HashSet<>(pool).size()==pool.size() && participants.containsAll(pool) && pool.stream().allMatch(id->eligible(id,parent.courseClassId())),"INVALID_WHEEL_POOL");require(k!=null && k>=1 && k<=pool.size(),"INVALID_WHEEL_K");
    var cells=wheel.config(parent.courseClassId()).path("cells");require(!cells.isEmpty(),"WHEEL_CONFIG_REQUIRED");
    var shuffled=new ArrayList<>(pool);Collections.shuffle(shuffled,random);
    state.set("pool",json.valueToTree(pool));state.set("selected",json.valueToTree(shuffled.subList(0,k)));state.set("cells",cells.deepCopy());state.set("grades",json.createObjectNode());state.put("status","SELECTED");
   }
   case "spin" -> {
    require(old!=null && old.path("status").asText().equals("SELECTED"),"WHEEL_ALREADY_SPUN");
    state.put("challengeIndex",random.nextInt(state.path("cells").size()));long now=System.currentTimeMillis();state.put("spinStartedAtMs",now);state.put("spinEndsAtMs",now+3000);state.put("status","SPUN");
    for(var id:state.path("pool"))if(!contains(state.path("selected"),id.asLong()))credit(lesson,game.id(),id.asLong(),eligible(id.asLong(),parent.courseClassId())?game.budget():BigDecimal.ZERO);
   }
   case "grade" -> {
    require(old!=null && old.path("status").asText().equals("SPUN"),"WHEEL_NOT_GRADABLE");require(grades!=null && !grades.isEmpty(),"INVALID_GRADES");
    for(var entry:grades.entrySet()){var score=entry.getValue();require(contains(state.path("selected"),entry.getKey()),"GRADE_NON_SELECTED");require(score!=null && score.signum()>=0 && score.scale()<=2 && score.compareTo(game.budget())<=0,"INVALID_GAME_SCORE");((ObjectNode)state.path("grades")).put(entry.getKey().toString(),score);}
   }
   case "finalize" -> {
    require(old!=null && old.path("status").asText().equals("SPUN"),"WHEEL_NOT_GRADABLE");
    for(long student:participants){BigDecimal score=BigDecimal.ZERO;if(contains(state.path("pool"),student))score=contains(state.path("selected"),student)?state.path("grades").path(Long.toString(student)).decimalValue():game.budget();if(!eligible(student,parent.courseClassId()))score=BigDecimal.ZERO;credit(lesson,game.id(),student,score);}
    realtime.window(lesson).filter(w->w.gameId()==game.id()).ifPresent(w->realtime.close(lesson,w.token(),"CLOSED"));lessons.gameStatus(game.id(),"COMPLETED");lessons.finishIfReady(lesson);state.put("status","COMPLETED");
   }
   default -> throw LessonFailure.conflict("INVALID_WHEEL_ACTION");
  }
  state.put("revision",revision+1);wheel.save(lesson,game.id(),state);wheel.receipt(actor,request,hash);lessons.bump(lesson);events.publishEvent(new LessonChanged(lesson));return state;
 }
 private void credit(long lesson,long game,long student,BigDecimal score){wheel.score(lesson,game,student,score.setScale(2));lessons.total(lesson,student);}
 private boolean contains(JsonNode list,long id){for(var value:list)if(value.asLong()==id)return true;return false;}
 public JsonNode view(long lesson,long user,boolean teacher){var state=wheel.round(lesson);if(state==null)return null;var result=((ObjectNode)state).deepCopy();if(!teacher){result.remove("grades");boolean selected=contains(state.path("selected"),user);result.put("selectedForChallenge",selected);if(selected)result.put("notification","Giảng viên đã chọn bạn để thực hiện vòng quay.");}return result;}
}
