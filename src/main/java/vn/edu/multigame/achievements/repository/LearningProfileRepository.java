package vn.edu.multigame.achievements.repository;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.multigame.achievements.dto.LearningProfile.*;
@Repository @Profile("mysql")
public class LearningProfileRepository {
 private final JdbcTemplate jdbc;
 public LearningProfileRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String SCOPE=" FROM lesson l JOIN course_class c ON c.id=l.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester sem ON sem.id=o.semester_id JOIN academic_subject sub ON sub.id=o.subject_id JOIN academic_year yr ON yr.id=sem.academic_year_id ";
 public record Identity(long id,String name,String code,String status){}
 public Optional<Identity> identity(long id){return jdbc.query("SELECT id,display_name,ma_sinh_vien,account_status FROM app_user WHERE id=? AND role='SINH_VIEN'",(r,n)->new Identity(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)),id).stream().findFirst();}
 private String filter(Long subject,Long year,Long course,List<Object> args){String sql="";if(subject!=null){sql+=" AND o.subject_id=?";args.add(subject);}if(year!=null){sql+=" AND sem.academic_year_id=?";args.add(year);}if(course!=null){sql+=" AND c.id=?";args.add(course);}return sql;}
 public Stats stats(long student){
  long attended=jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE student_id=?",Long.class,student);
  long completed=jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance a JOIN lesson l ON l.id=a.lesson_id WHERE a.student_id=? AND l.status='COMPLETED'",Long.class,student);
  long games=jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game_participant p JOIN lesson_game g ON g.id=p.game_id WHERE p.student_id=? AND g.status='COMPLETED'",Long.class,student);
  var points=jdbc.queryForObject("SELECT COALESCE(SUM(score),0) FROM lesson_score WHERE student_id=?",java.math.BigDecimal.class,student);
  long questions=jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(g.question_count,JSON_LENGTH(c.questions))),0) FROM lesson_game_participant p JOIN lesson_game g ON g.id=p.game_id JOIN lesson_game_content c ON c.game_id=g.id WHERE p.student_id=? AND g.mode='QUIZ' AND g.status='COMPLETED'",Long.class,student);
  long correct=jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer a JOIN lesson_game g ON g.id=a.game_id WHERE a.student_id=? AND a.is_correct=TRUE AND g.mode='QUIZ' AND g.status='COMPLETED'",Long.class,student);
  return new Stats(attended,completed,games,points,questions,correct);
 }
 public List<Award> awards(long student,Long subject,Long year){
  var args=new ArrayList<Object>();args.add(student);String conditions=filter(subject,year,null,args);
  var result=new ArrayList<>(jdbc.query("SELECT a.id,a.lesson_game_id,a.earned_at_ms,o.subject_id,sem.academic_year_id FROM achievement_award a JOIN lesson_game g ON g.id=a.lesson_game_id JOIN lesson l ON l.id=g.lesson_id JOIN course_class c ON c.id=l.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN academic_semester sem ON sem.id=o.semester_id WHERE a.user_id=? AND a.award_type='PERFECT' AND g.mode='QUIZ' AND g.status='COMPLETED'"+conditions+" ORDER BY a.earned_at_ms DESC,a.id DESC",(r,n)->new Award(r.getLong(1),r.getLong(2),null,"PERFECT",perfectLabel(),r.getLong(3),r.getLong(4),r.getLong(5)),args.toArray()));
  if(subject==null && year==null)result.addAll(jdbc.query("SELECT a.id,a.game_session_id,a.earned_at_ms FROM achievement_award a JOIN game_session g ON g.id=a.game_session_id JOIN game_award_decision d ON d.game_session_id=g.id JOIN game_award_participant p ON p.game_session_id=g.id AND p.user_id=a.user_id WHERE a.user_id=? AND a.award_type='PERFECT' AND d.state='DECIDED' AND p.eligible=1 AND p.official_game_delta=1 AND g.status='FINISHED' AND g.end_reason='COMPLETED' AND EXISTS(SELECT 1 FROM game_question q WHERE q.game_session_id=g.id) AND NOT EXISTS(SELECT 1 FROM game_question q WHERE q.game_session_id=g.id AND q.mode<>'QUIZ')",(r,n)->new Award(r.getLong(1),null,r.getLong(2),"PERFECT",perfectLabel(),r.getLong(3),null,null),student));
  result.sort(Comparator.comparingLong(Award::earnedAtMs).thenComparingLong(Award::id).reversed());return List.copyOf(result);
 }
 private String perfectLabel(){return "B\u00e1ch ph\u00e1t b\u00e1ch tr\u00fang";}
 public History history(long student,Long subject,Long year,Long course,int page,int size){
  var args=new ArrayList<Object>();args.add(student);String where=" JOIN lesson_attendance a ON a.lesson_id=l.id WHERE a.student_id=?"+filter(subject,year,course,args);
  long total=jdbc.queryForObject("SELECT COUNT(*)"+SCOPE+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
  var rows=jdbc.query("SELECT l.id,c.id,c.name,o.subject_id,sub.name,sem.academic_year_id,yr.name,l.lesson_number,l.title,l.status,COALESCE((SELECT score FROM lesson_score s WHERE s.lesson_id=l.id AND s.student_id=a.student_id),0) points"+SCOPE+where+" ORDER BY sem.academic_year_id DESC,c.id,l.lesson_number DESC LIMIT ? OFFSET ?",(r,n)->new Lesson(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getString(5),r.getLong(6),r.getString(7),r.getInt(8),r.getString(9),r.getString(10),r.getBigDecimal(11),games(r.getLong(1),student)),args.toArray());return new History(rows,page,size,total);
 }
 private List<Game> games(long lesson,long student){return jdbc.query("SELECT g.id,g.position,g.mode,g.status,EXISTS(SELECT 1 FROM lesson_game_participant p WHERE p.game_id=g.id AND p.student_id=?),COALESCE((SELECT score FROM lesson_game_score s WHERE s.game_id=g.id AND s.student_id=?),0) FROM lesson_game g WHERE g.lesson_id=? ORDER BY g.position",(r,n)->new Game(r.getLong(1),r.getInt(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getBigDecimal(6)),student,student,lesson);}
}
