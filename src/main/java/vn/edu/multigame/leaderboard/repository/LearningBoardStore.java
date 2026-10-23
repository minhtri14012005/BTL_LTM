package vn.edu.multigame.leaderboard.repository;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import vn.edu.multigame.leaderboard.dto.LearningBoard.*;
@Repository @Profile("mysql")
public class LearningBoardStore {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public LearningBoardStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 private static final String CLASS_SCOPE=" FROM class_room r JOIN course_class c ON c.id=r.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester sem ON sem.id=o.semester_id ";
 private List<Student> students(String scope,Object... args){
  String sql="SELECT u.id,u.ma_sinh_vien,u.display_name,COALESCE(t.points,0) AS points FROM app_user u JOIN (SELECT DISTINCT m.student_id"+CLASS_SCOPE+" JOIN class_membership m ON m.class_room_id=r.id WHERE m.status IN ('APPROVED','REMOVED') AND "+scope+") members ON members.student_id=u.id LEFT JOIN (SELECT s.student_id,SUM(s.score) AS points FROM lesson_score s JOIN lesson l ON l.id=s.lesson_id JOIN class_room r ON r.course_class_id=l.course_class_id JOIN course_class c ON c.id=r.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester sem ON sem.id=o.semester_id WHERE "+scope+" GROUP BY s.student_id) t ON t.student_id=u.id ORDER BY points DESC,u.ma_sinh_vien,u.id";
  var params=new ArrayList<Object>(Arrays.asList(args));params.addAll(Arrays.asList(args));return jdbc.query(sql,(r,n)->new Student(r.getLong(1),r.getString(2),r.getString(3),r.getBigDecimal(4)),params.toArray());
 }
 public List<Student> classroom(long id){return students("c.id=?",id);}
 public List<Student> global(long subject,long year){return students("o.subject_id=? AND sem.academic_year_id=?",subject,year);}
 public boolean scope(long subject,long year){return jdbc.queryForObject("SELECT COUNT(*)"+CLASS_SCOPE+" WHERE o.subject_id=? AND sem.academic_year_id=?",Integer.class,subject,year)>0;}
 private List<Range> read(String value){try{return List.of(json.readValue(value,Range[].class));}catch(Exception e){throw new IllegalStateException(e);}}
 private String write(List<Range> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 public Config config(long course){return jdbc.query("SELECT revision,ranges_json FROM class_rank_settings WHERE course_class_id=?",(r,n)->new Config(r.getLong(1),read(r.getString(2))),course).stream().findFirst().orElse(new Config(0,List.of()));}
 public void save(long course,List<Range> ranges){jdbc.update("INSERT INTO class_rank_settings VALUES(?,1,?) ON DUPLICATE KEY UPDATE revision=revision+1,ranges_json=VALUES(ranges_json)",course,write(ranges));}
 public void share(String code,List<Range> ranges){jdbc.update("INSERT INTO class_rank_share VALUES(?,?)",code,write(ranges));}
 public Optional<List<Range>> shared(String code){return jdbc.query("SELECT ranges_json FROM class_rank_share WHERE code=?",(r,n)->read(r.getString(1)),code).stream().findFirst();}
}
