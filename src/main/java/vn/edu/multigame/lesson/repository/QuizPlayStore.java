package vn.edu.multigame.lesson.repository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
@Repository @Profile("mysql")
public class QuizPlayStore {
 private final JdbcTemplate jdbc;
 public QuizPlayStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public int opened(long game){return jdbc.queryForObject("SELECT COALESCE(MAX(question_index),0) FROM lesson_quiz_question WHERE game_id=?",Integer.class,game);}
 public void open(long game,int question){jdbc.update("INSERT INTO lesson_quiz_question(game_id,question_index) VALUES(?,?)",game,question);}
 public boolean answered(long game,int question,long student){return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer WHERE game_id=? AND question_index=? AND student_id=?",Integer.class,game,question,student)>0;}
 public void answer(long game,int question,long student,String value,boolean correct,long received){jdbc.update("INSERT INTO lesson_quiz_answer VALUES(?,?,?,?,?,?)",game,question,student,value,correct,received);}
 public int correct(long game,long student){return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer WHERE game_id=? AND student_id=? AND is_correct=TRUE",Integer.class,game,student);}
 public List<Map<String,Object>> results(long game,long student){return jdbc.queryForList("SELECT question_index AS questionIndex,is_correct AS correct,answer_value AS answerValue,received_at_ms AS receivedAtMs FROM lesson_quiz_answer WHERE game_id=? AND student_id=? ORDER BY question_index",game,student);}
 public void score(long lesson,long game,long student,java.math.BigDecimal points){jdbc.update("INSERT INTO lesson_game_score VALUES(?,?,?) ON DUPLICATE KEY UPDATE score=VALUES(score)",game,student,points);}
 public void absent(long game){jdbc.update("INSERT INTO lesson_game_score(game_id,student_id,score) SELECT game_id,student_id,0 FROM lesson_game_participant p WHERE p.game_id=? AND NOT EXISTS(SELECT 1 FROM lesson_game_score s WHERE s.game_id=p.game_id AND s.student_id=p.student_id)",game);}
}
