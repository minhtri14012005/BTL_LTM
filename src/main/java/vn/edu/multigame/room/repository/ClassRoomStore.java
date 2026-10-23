package vn.edu.multigame.room.repository;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.room.dto.response.*;

/** Class membership is durable authority, independent of socket/attendance state. */
@Repository @Profile("mysql")
public class ClassRoomStore {
    private final JdbcTemplate jdbc;
    public ClassRoomStore(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private static final String ROOM="SELECT r.id,r.course_class_id,r.join_code,o.subject_id,s.academic_year_id FROM class_room r JOIN course_class c ON c.id=r.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester s ON s.id=o.semester_id";
    private static final String MEMBER="SELECT m.*,r.course_class_id,u.display_name,u.ma_sinh_vien,c.code class_code,c.name class_name,c.required_lessons,o.subject_id,sem.academic_year_id,sub.name subject_name,y.name academic_year_name FROM class_membership m JOIN class_room r ON r.id=m.class_room_id JOIN app_user u ON u.id=m.student_id JOIN course_class c ON c.id=r.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_subject sub ON sub.id=o.subject_id JOIN academic_semester sem ON sem.id=o.semester_id JOIN academic_year y ON y.id=sem.academic_year_id";
    private static final org.springframework.jdbc.core.RowMapper<ClassRoomView> ROOM_ROW=(r,n)->new ClassRoomView(r.getLong("id"),r.getLong("course_class_id"),r.getLong("subject_id"),r.getLong("academic_year_id"),r.getString("join_code"));
    private static final org.springframework.jdbc.core.RowMapper<ClassMembershipView> MEMBER_ROW=(r,n)->new ClassMembershipView(r.getLong("id"),r.getLong("class_room_id"),r.getLong("course_class_id"),r.getLong("student_id"),r.getString("display_name"),r.getString("ma_sinh_vien"),r.getString("status"),r.getLong("revision"),r.getLong("requested_at_ms"),r.getObject("reviewed_at_ms",Long.class),r.getObject("reviewer_id",Long.class),r.getString("class_code"),r.getString("class_name"),r.getString("subject_name"),r.getString("academic_year_name"),r.getInt("required_lessons"),r.getLong("subject_id"),r.getLong("academic_year_id"));
    public void create(long classId) {
        jdbc.update("INSERT INTO class_room(course_class_id,join_code) VALUES(?,?)",classId,UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT));
    }
    public Optional<ClassRoomView> forClass(long classId) {return jdbc.query(ROOM+" WHERE r.course_class_id=?",ROOM_ROW,classId).stream().findFirst();}
    public Optional<ClassRoomView> byCode(String code) {return jdbc.query(ROOM+" WHERE r.join_code=?",ROOM_ROW,code).stream().findFirst();}
    public boolean assigned(long classId,long teacher) {return jdbc.queryForObject("SELECT COUNT(*) FROM teaching_assignment WHERE course_class_id=? AND teacher_id=?",Integer.class,classId,teacher)>0;}
    public List<Long> teachers(long classId) {return jdbc.queryForList("SELECT teacher_id FROM teaching_assignment WHERE course_class_id=?",Long.class,classId);}
    public boolean excluded(long student,ClassRoomView room) {return jdbc.queryForObject("SELECT COUNT(*) FROM subject_year_exclusion WHERE student_id=? AND subject_id=? AND academic_year_id=?",Integer.class,student,room.subjectId(),room.academicYearId())>0;}
    public Optional<ClassMembershipView> member(long room,long student) {return jdbc.query(MEMBER+" WHERE m.class_room_id=? AND m.student_id=?",MEMBER_ROW,room,student).stream().findFirst();}
    public List<ClassMembershipView> roster(long room) {return jdbc.query(MEMBER+" WHERE m.class_room_id=? ORDER BY m.id",MEMBER_ROW,room);}
    public List<ClassMembershipView> mine(long student) {return jdbc.query(MEMBER+" WHERE m.student_id=? ORDER BY m.id",MEMBER_ROW,student);}
    public void request(long room,long student,long now) {jdbc.update("INSERT INTO class_membership(class_room_id,student_id,status,requested_at_ms) VALUES(?,?,'PENDING',?)",room,student,now);}
    public void review(long member,String status,long teacher,long now) {jdbc.update("UPDATE class_membership SET status=?,revision=revision+1,reviewed_at_ms=?,reviewer_id=? WHERE id=?",status,now,teacher,member);}
    public void exclude(long student,ClassRoomView room,long teacher,long now) {
        jdbc.update("INSERT INTO subject_year_exclusion(student_id,subject_id,academic_year_id,source_class_id,teacher_id,removed_at_ms) VALUES(?,?,?,?,?,?)",student,room.subjectId(),room.academicYearId(),room.courseClassId(),teacher,now);
        jdbc.update("UPDATE class_membership m JOIN class_room r ON r.id=m.class_room_id JOIN course_class c ON c.id=r.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester s ON s.id=o.semester_id SET m.status='REMOVED',m.revision=m.revision+1 WHERE m.student_id=? AND o.subject_id=? AND s.academic_year_id=? AND m.status<>'REMOVED'",student,room.subjectId(),room.academicYearId());
    }
    public Optional<String> receipt(long actor,String request) {return jdbc.query("SELECT fingerprint FROM class_membership_action WHERE actor_id=? AND request_id=?",(r,n)->r.getString(1),actor,request).stream().findFirst();}
    public void record(long actor,String request,String fingerprint,String command,ClassMembershipView member,long now) {
        jdbc.update("INSERT INTO class_membership_action(actor_id,request_id,fingerprint,command,membership_id,result_status,result_revision,created_at_ms) VALUES(?,?,?,?,?,?,?,?)",actor,request,fingerprint,command,member.id(),member.status(),member.revision(),now);
    }
}
