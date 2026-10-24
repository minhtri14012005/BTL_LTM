package vn.edu.multigame.leaderboard.service;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.security.core.Authentication;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.multigame.lesson.repository.LessonStore;
import vn.edu.multigame.room.repository.ClassRoomStore;
@Component @Profile("mysql")
public class LearningBoardRealtime {
 private final LearningBoardService boards;private final AuthenticatedSocketRegistry sockets;private final LessonStore lessons;private final ClassRoomStore rooms;
 public LearningBoardRealtime(LearningBoardService boards,AuthenticatedSocketRegistry sockets,LessonStore lessons,ClassRoomStore rooms){this.boards=boards;this.sockets=sockets;this.lessons=lessons;this.rooms=rooms;}
 @TransactionalEventListener
 public void changed(LearningScoreChanged event){
  var source=rooms.forClass(event.classId()).orElseThrow();var delivered=new HashSet<String>();
  // Pages outside an active lesson refetch through their own authorized REST endpoint.
  // This notification contains no roster, score, hidden content or rank configuration.
  var invalidation=Map.of("v",1,"kind","EVENT","type","SCORES_INVALIDATED","classId",event.classId(),"subjectId",source.subjectId(),"academicYearId",source.academicYearId());
  for(long user:sockets.onlineUsers())sockets.sendToUser(user,invalidation,()->true);
  for(var subscription:sockets.lessonSubscriptions().entrySet()) {
   long course;
   try{course=lessons.lesson(subscription.getKey(),false).courseClassId();}catch(vn.edu.multigame.lesson.service.LessonFailure deleted){continue;}var room=rooms.forClass(course).orElseThrow();
   if(room.subjectId()!=source.subjectId() || room.academicYearId()!=source.academicYearId())continue;
   for(var socket:subscription.getValue()){
    if(!delivered.add(socket.getId()+":"+course))continue;
    var auth=(Authentication)socket.getPrincipal();
    try {
     var classroom=boards.classroom(auth,course);var global=boards.global(auth,room.subjectId(),room.academicYearId());
     sockets.send(socket,Map.of("v",1,"kind","EVENT","type","SCORES_UPDATED","eventId",UUID.randomUUID().toString(),"serverTimeMs",System.currentTimeMillis(),"refresh",true,"classroom",classroom,"global",global),()->{
      try{sockets.requireCurrent(socket);boards.classroom(auth,course);return true;}catch(RuntimeException denied){return false;}
     });
    }catch(vn.edu.multigame.auth.service.AuthFailure | vn.edu.multigame.room.service.RoomFailure | vn.edu.multigame.lesson.service.LessonFailure denied){/* No board data is sent after membership or session revocation. */}
    catch(org.springframework.dao.DataAccessException unavailable){sockets.close(socket,new org.springframework.web.socket.CloseStatus(1011,"DATABASE_UNAVAILABLE"));}
   }
  }
 }
}
