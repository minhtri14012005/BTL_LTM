package vn.edu.multigame.achievements.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.multigame.achievements.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
import vn.edu.multigame.lesson.repository.*;
import vn.edu.multigame.room.repository.ClassRoomStore;
@Service @Profile("mysql")
public class LearningAwardService {
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final AccountAccessPolicy accounts;private final ClassRoomStore rooms;private final QuizPlayStore quiz;
 public LearningAwardService(JdbcTemplate jdbc,ObjectMapper json,AccountAccessPolicy accounts,ClassRoomStore rooms,QuizPlayStore quiz){this.jdbc=jdbc;this.json=json;this.accounts=accounts;this.rooms=rooms;this.quiz=quiz;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void captureInitialPlayers(long game){jdbc.update("INSERT INTO lesson_quiz_award_roster(game_id,student_id) SELECT game_id,student_id FROM lesson_game_participant WHERE game_id=?",game);}
 @Transactional(propagation=Propagation.MANDATORY)
 public void joinedFirstQuestion(long game,long student){jdbc.update("INSERT IGNORE INTO lesson_quiz_award_roster(game_id,student_id) VALUES(?,?)",game,student);}
 /** Called within the game completion transaction after account and parent locks. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void quizCompleted(long game,long course,int total,List<Long> participants,long now){
  var initial=jdbc.queryForList("SELECT student_id FROM lesson_quiz_award_roster WHERE game_id=? ORDER BY student_id",Long.class,game);
  participants=initial.stream().filter(participants::contains).toList();
  var room=rooms.forClass(course).orElseThrow();var players=new ArrayList<PerfectAwardPolicy.Player>();
  for(long student:participants){var state=accounts.state(student);boolean eligible=state.deletedAtMs()==null && !state.banned(now) && !rooms.excluded(student,room) && rooms.member(room.id(),student).map(m->m.status().equals("APPROVED")).orElse(false);players.add(new PerfectAwardPolicy.Player(student,eligible,quiz.correct(game,student)));}
  var winners=PerfectAwardPolicy.winners(total,quiz.opened(game),players);
  long eligible=players.stream().filter(PerfectAwardPolicy.Player::eligible).count();
  for(long student:winners)jdbc.update("INSERT INTO achievement_award(lesson_game_id,user_id,award_type,earned_at_ms,evidence_snapshot) SELECT ?,?,'PERFECT',?,? WHERE NOT EXISTS(SELECT 1 FROM achievement_award WHERE lesson_game_id=? AND user_id=? AND award_type='PERFECT')",game,student,now,encode(Map.of("questionCount",total,"correctCount",total,"eligiblePlayers",eligible)),game,student);
 }
 private String encode(Object evidence){try{return json.writeValueAsString(evidence);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
}
