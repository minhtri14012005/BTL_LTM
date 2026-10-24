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
    private final org.springframework.jdbc.core.RowMapper<ClassRoomView> ROOM_ROW=(r,n)->new ClassRoomView(r.getLong("id"),r.getLong("course_class_id"),r.getLong("subject_id"),r.getLong("academic_year_id"),r.getString("join_code"),!teachers(r.getLong("course_class_id")).isEmpty());
    private static final org.springframework.jdbc.core.RowMapper<ClassMembershipView> MEMBER_ROW=(r,n)->new ClassMembershipView(r.getLong("id"),r.getLong("class_room_id"),r.getLong("course_class_id"),r.getLong("student_id"),r.getString("display_name"),r.getString("ma_sinh_vien"),r.getString("status"),r.getLong("revision"),r.getLong("requested_at_ms"),r.getObject("reviewed_at_ms",Long.class),r.getObject("reviewer_id",Long.class),r.getString("class_code"),r.getString("class_name"),r.getString("subject_name"),r.getString("academic_year_name"),r.getInt("required_lessons"),r.getLong("subject_id"),r.getLong("academic_year_id"));
    public void create(long classId) {
        jdbc.update("INSERT INTO class_room(course_class_id,join_code) VALUES(?,?)",classId,UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT));
    }
    public Optional<ClassRoomView> forClass(long classId) {return jdbc.query(ROOM+" WHERE r.course_class_id=?",ROOM_ROW,classId).stream().findFirst();}
    public Optional<ClassRoomView> byCode(String code) {return jdbc.query(ROOM+" WHERE r.join_code=?",ROOM_ROW,code).stream().findFirst();}
    private static final String ACTIVE_TEACHERS=" FROM teaching_assignment a JOIN app_user u ON u.id=a.teacher_id WHERE a.course_class_id=? AND u.role='GIANG_VIEN' AND u.deleted_at_ms IS NULL AND (u.ban_kind='NONE' OR u.ban_kind='TEMP' AND u.banned_until_ms<=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000)";
    public boolean assigned(long classId,long teacher) {return jdbc.queryForObject("SELECT COUNT(*)"+ACTIVE_TEACHERS+" AND a.teacher_id=?",Integer.class,classId,teacher)>0;}
    public List<Long> teachers(long classId) {return jdbc.queryForList("SELECT a.teacher_id"+ACTIVE_TEACHERS,Long.class,classId);}
    public void requireStaff(long classId) {
        jdbc.queryForObject("SELECT id FROM course_class WHERE id=? FOR UPDATE",Long.class,classId);
        if(teachers(classId).isEmpty())throw new vn.edu.multigame.auth.service.AuthFailure(org.springframework.http.HttpStatus.CONFLICT,"CLASS_AWAITING_TEACHER","Lớp chưa có giảng viên tiếp quản. Vui lòng chờ quản trị viên phân công giảng viên mới.");
    }
    public boolean excluded(long student,ClassRoomView room) {return jdbc.queryForObject("SELECT COUNT(*) FROM subject_year_exclusion WHERE student_id=? AND subject_id=? AND academic_year_id=?",Integer.class,student,room.subjectId(),room.academicYearId())>0;}
    public Optional<ClassMembershipView> member(long room,long student) {return jdbc.query(MEMBER+" WHERE m.class_room_id=? AND m.student_id=?",MEMBER_ROW,room,student).stream().findFirst();}
    public List<ClassMembershipView> roster(long room) {return jdbc.query(MEMBER+" WHERE m.class_room_id=? ORDER BY m.id",MEMBER_ROW,room);}
    public List<ClassMembershipView> mine(long student) {return jdbc.query(MEMBER+" WHERE m.student_id=? ORDER BY m.id",MEMBER_ROW,student);}
    public void request(long room,long student,long now) {jdbc.update("INSERT INTO class_membership(class_room_id,student_id,status,requested_at_ms) VALUES(?,?,'PENDING',?)",room,student,now);}
    public void review(long member,String status,long teacher,long now) {jdbc.update("UPDATE class_membership SET status=?,revision=revision+1,reviewed_at_ms=?,reviewer_id=? WHERE id=?",status,now,teacher,member);}
    public void exclude(long student,ClassRoomView room,long teacher,long now) {
        jdbc.update("INSERT INTO subject_year_exclusion(student_id,subject_id,academic_year_id,source_class_id,teacher_id,removed_at_ms) VALUES(?,?,?,?,?,?)",student,room.subjectId(),room.academicYearId(),room.courseClassId(),teacher,now);
        jdbc.update("UPDATE class_membership SET removed_from_status=status,status='REMOVED',revision=revision+1 WHERE student_id=? AND class_room_id IN (SELECT r.id FROM class_room r JOIN course_class c ON c.id=r.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester s ON s.id=o.semester_id WHERE o.subject_id=? AND s.academic_year_id=?) AND status<>'REMOVED'",student,room.subjectId(),room.academicYearId());
    }
    public void restore(long student,ClassRoomView room,long teacher) {
        var sources=jdbc.queryForList("SELECT source_class_id FROM subject_year_exclusion WHERE student_id=? AND subject_id=? AND academic_year_id=?",Long.class,student,room.subjectId(),room.academicYearId());
        if(sources.isEmpty())throw vn.edu.multigame.room.service.RoomFailure.conflict("EXCLUSION_NOT_FOUND");
        if(!assigned(sources.getFirst(),teacher))throw new vn.edu.multigame.auth.service.AuthFailure(org.springframework.http.HttpStatus.FORBIDDEN,"RESTORE_SOURCE_CLASS_REQUIRED","Cần giảng viên phụ trách lớp đã thực hiện thao tác loại khôi phục sinh viên.");
        jdbc.update("UPDATE class_membership SET status=COALESCE(removed_from_status,'PENDING'),removed_from_status=NULL,revision=revision+1 WHERE student_id=? AND class_room_id IN (SELECT r.id FROM class_room r JOIN course_class c ON c.id=r.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester s ON s.id=o.semester_id WHERE o.subject_id=? AND s.academic_year_id=?) AND status='REMOVED'",student,room.subjectId(),room.academicYearId());
        jdbc.update("DELETE FROM subject_year_exclusion WHERE student_id=? AND subject_id=? AND academic_year_id=?",student,room.subjectId(),room.academicYearId());
    }
    public Optional<Map<String,Object>> batch(long actor,String request){return jdbc.queryForList("SELECT fingerprint,result_json FROM class_membership_batch WHERE actor_id=? AND request_id=?",actor,request).stream().findFirst();}
    public void recordBatch(long actor,String request,long course,String fingerprint,String result){jdbc.update("INSERT INTO class_membership_batch VALUES(?,?,?,?,?,?)",actor,request,course,fingerprint,result,System.currentTimeMillis());}
    public Optional<String> receipt(long actor,String request) {return jdbc.query("SELECT fingerprint FROM class_membership_action WHERE actor_id=? AND request_id=?",(r,n)->r.getString(1),actor,request).stream().findFirst();}
    public void record(long actor,String request,String fingerprint,String command,ClassMembershipView member,long now) {
        jdbc.update("INSERT INTO class_membership_action(actor_id,request_id,fingerprint,command,membership_id,result_status,result_revision,created_at_ms) VALUES(?,?,?,?,?,?,?,?)",actor,request,fingerprint,command,member.id(),member.status(),member.revision(),now);
    }
}
