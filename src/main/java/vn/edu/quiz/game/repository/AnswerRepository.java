package vn.edu.quiz.game.repository;

import vn.edu.quiz.game.entity.Answer;
import vn.edu.quiz.game.enums.AnswerStatus;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByGameSessionId(Long gameSessionId);
    List<Answer> findByGameQuestionId(Long gameQuestionId);
    Optional<Answer> findByPlayerSessionIdAndGameQuestionId(Long playerSessionId, Long gameQuestionId);
    List<Answer> findByGameSessionIdAndAnswerStatus(Long gameSessionId, AnswerStatus answerStatus);
}
