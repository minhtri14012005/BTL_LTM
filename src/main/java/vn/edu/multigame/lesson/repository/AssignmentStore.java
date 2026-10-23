package vn.edu.multigame.lesson.repository;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
@Repository @Profile("mysql")
public class AssignmentStore {
 public record Submission(long id,long studentId,String requestId,String text,long receivedAtMs){}
 public record Grade(long studentId,long submissionId,BigDecimal score,long gradedAtMs){}
 public record View(List<Submission> latest,List<Grade> grades,Long deadlineAtMs){}
 private final JdbcTemplate jdbc;
 public AssignmentStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Submission> history(long game,long student,long after,int limit){return jdbc.query("SELECT * FROM lesson_action_input WHERE game_id=? AND student_id=? AND id>? ORDER BY id LIMIT ?",(r,n)->new Submission(r.getLong("id"),student,r.getString("request_id"),r.getString("input_value"),r.getLong("received_at_ms")),game,student,after,limit);}
 public Optional<Submission> latest(long game,long student){return jdbc.query("SELECT * FROM lesson_action_input WHERE game_id=? AND student_id=? ORDER BY id DESC LIMIT 1",(r,n)->new Submission(r.getLong("id"),student,r.getString("request_id"),r.getString("input_value"),r.getLong("received_at_ms")),game,student).stream().findFirst();}
 public Optional<Grade> grade(long game,long student){return jdbc.query("SELECT * FROM lesson_assignment_grade WHERE game_id=? AND student_id=?",(r,n)->new Grade(student,r.getLong("submission_id"),r.getBigDecimal("score"),r.getLong("graded_at_ms")),game,student).stream().findFirst();}
 public void grade(long game,long student,long submission,BigDecimal score,long now){jdbc.update("INSERT INTO lesson_assignment_grade VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE submission_id=VALUES(submission_id),score=VALUES(score),graded_at_ms=VALUES(graded_at_ms)",game,student,submission,score,now);}
 public Optional<Long> deadline(long game){return jdbc.query("SELECT deadline_at_ms FROM lesson_assignment_deadline WHERE game_id=?",(r,n)->r.getLong(1),game).stream().findFirst();}
 public void deadline(long game,long deadline){jdbc.update("INSERT INTO lesson_assignment_deadline VALUES(?,?) ON DUPLICATE KEY UPDATE deadline_at_ms=VALUES(deadline_at_ms)",game,deadline);}
 public View view(long game,List<Long> students){var latest=new ArrayList<Submission>();var grades=new ArrayList<Grade>();for(long student:students){latest(game,student).ifPresent(latest::add);grade(game,student).ifPresent(grades::add);}return new View(latest,grades,deadline(game).orElse(null));}
}
