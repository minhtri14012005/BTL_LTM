package vn.edu.multigame.lesson.service;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.multigame.lesson.repository.LessonStore;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
/** Class list invalidation never identifies an unpublished lesson or reveals its content. */
@Component @Profile("mysql")
public class LessonListNotifier {
    private final LessonStore lessons;private final ClassRoomStore rooms;private final AuthenticatedSocketRegistry sockets;
    public LessonListNotifier(LessonStore lessons,ClassRoomStore rooms,AuthenticatedSocketRegistry sockets){this.lessons=lessons;this.rooms=rooms;this.sockets=sockets;}
    @TransactionalEventListener public void changed(LessonChanged event){
        notifyClass(lessons.lesson(event.lessonId(),false).courseClassId());
    }
    @TransactionalEventListener public void deleted(LessonDeleted event){notifyClass(event.classId());}
    private void notifyClass(long course){
        var room=rooms.forClass(course).orElseThrow();
        var message=Map.of("v",1,"kind","EVENT","type","CLASS_LESSONS_UPDATED","target",Map.of("kind","CLASS","id",course));
        for(long teacher:rooms.teachers(course))sockets.sendToUser(teacher,message,()->rooms.assigned(course,teacher));
        for(var member:rooms.roster(room.id()))if(member.status().equals("APPROVED"))sockets.sendToUser(member.studentId(),message,()->rooms.member(room.id(),member.studentId()).map(m->m.status().equals("APPROVED")).orElse(false)&&!rooms.excluded(member.studentId(),room));
    }
}
