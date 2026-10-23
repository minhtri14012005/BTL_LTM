package vn.edu.multigame.achievements.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.multigame.achievements.model.*;
import vn.edu.multigame.achievements.repository.AwardRepository;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
import vn.edu.multigame.lesson.repository.*;
import vn.edu.multigame.room.repository.ClassRoomStore;
@Service @Profile("mysql")
public class LearningAwardService {
 private final JdbcTemplate jdbc;private final AwardRepository ledger;private final AccountAccessPolicy accounts;private final ClassRoomStore rooms;private final QuizPlayStore quiz;
 public LearningAwardService(JdbcTemplate jdbc,AwardRepository ledger,AccountAccessPolicy accounts,ClassRoomStore rooms,QuizPlayStore quiz){this.jdbc=jdbc;this.ledger=ledger;this.accounts=accounts;this.rooms=rooms;this.quiz=quiz;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void captureInitialPlayers(long game){jdbc.update("INSERT INTO lesson_quiz_award_roster(game_id,student_id) SELECT game_id,student_id FROM lesson_game_participant WHERE game_id=?",game);}
 /** Called within the game completion transaction after account and parent locks. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void quizCompleted(long game,long course,int total,List<Long> participants,long now){
  var initial=jdbc.queryForList("SELECT student_id FROM lesson_quiz_award_roster WHERE game_id=? ORDER BY student_id",Long.class,game);
  participants=initial.stream().filter(participants::contains).toList();
  var room=rooms.forClass(course).orElseThrow();var players=new ArrayList<AwardEvaluator.Player>();
  for(long student:participants){var state=accounts.state(student);boolean eligible=state.deletedAtMs()==null && !state.banned(now) && !rooms.excluded(student,room) && rooms.member(room.id(),student).map(m->m.status().equals("APPROVED")).orElse(false);players.add(new AwardEvaluator.Player(student,0,0,0,eligible,List.of(new AwardEvaluator.Metric("QUIZ",total,quiz.correct(game,student),0))));}
  var policy=ApprovedAwardPolicy.approved();
  // The old evaluator's participation thresholds remain authoritative; only PERFECT is enabled for lesson Quiz.
  var result=new AwardEvaluator().evaluate(policy,total,quiz.opened(game),true,players);
  for(var award:result.awards())if(award.type().equals("PERFECT"))jdbc.update("INSERT INTO achievement_award(game_session_id,lesson_game_id,user_id,award_type,mode_key,earned_at_ms,evidence_snapshot) SELECT NULL,?,?,'PERFECT','ALL',?,? WHERE NOT EXISTS(SELECT 1 FROM achievement_award WHERE lesson_game_id=? AND user_id=? AND award_type='PERFECT')",game,award.userId(),now,ledger.encode(Map.of("policy",policy,"questionCount",total,"correctCount",total,"eligiblePlayers",result.eligibleCount())),game,award.userId());
 }
}
