package vn.edu.multigame.game.repository;

import vn.edu.multigame.game.entity.Answer;
import vn.edu.multigame.game.enums.AnswerStatus;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByGameSessionId(Long gameSessionId);
    List<Answer> findByGameQuestionId(Long gameQuestionId);
    Optional<Answer> findByPlayerSessionIdAndGameQuestionId(Long playerSessionId, Long gameQuestionId);
    List<Answer> findByGameSessionIdAndAnswerStatus(Long gameSessionId, AnswerStatus answerStatus);
}
