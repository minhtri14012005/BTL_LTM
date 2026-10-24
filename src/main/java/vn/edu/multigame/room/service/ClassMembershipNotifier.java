package vn.edu.multigame.room.service;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
/** Post-commit invalidation only: no names, room code or private roster in the event. */
@Component @Profile("mysql")
public class ClassMembershipNotifier {
    private final ClassRoomStore rooms;private final AuthenticatedSocketRegistry sockets;
    public ClassMembershipNotifier(ClassRoomStore rooms,AuthenticatedSocketRegistry sockets){this.rooms=rooms;this.sockets=sockets;}
    @TransactionalEventListener public void changed(ClassMembershipChanged event){
        for(long course:rooms.mine(event.studentId()).stream().map(m->m.courseClassId()).distinct().toList()) {
            var message=Map.of("v",1,"kind","EVENT","type","CLASS_MEMBERSHIP_UPDATED","target",Map.of("kind","CLASS","id",course));
            for(long teacher:rooms.teachers(course))sockets.sendToUser(teacher,message,()->rooms.assigned(course,teacher));
            sockets.sendToUser(event.studentId(),message,()->true);
        }
    }
    @TransactionalEventListener public void staffing(ClassStaffingChanged event){
        var room=rooms.forClass(event.classId()).orElseThrow();
        var message=Map.of("v",1,"kind","EVENT","type","CLASS_STAFFING_UPDATED","target",Map.of("kind","CLASS","id",event.classId()));
        for(var member:rooms.roster(room.id()))sockets.sendToUser(member.studentId(),message,()->true);
        for(long teacher:rooms.teachers(event.classId()))sockets.sendToUser(teacher,message,()->rooms.assigned(event.classId(),teacher));
    }
}
