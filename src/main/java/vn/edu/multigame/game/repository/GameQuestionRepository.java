package vn.edu.multigame.game.repository;

import vn.edu.multigame.game.entity.GameQuestion;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface GameQuestionRepository extends JpaRepository<GameQuestion, Long> {
    List<GameQuestion> findByGameSessionIdOrderByOrderIndex(Long gameSessionId);
    Optional<GameQuestion> findByGameSessionIdAndOrderIndex(Long gameSessionId, Integer orderIndex);
}
