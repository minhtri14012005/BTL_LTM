package vn.edu.quiz.game.repository;

import vn.edu.quiz.game.entity.PlayerSession;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface PlayerSessionRepository extends JpaRepository<PlayerSession, Long> {
    List<PlayerSession> findByGameSessionId(Long gameSessionId);
    Optional<PlayerSession> findByGameSessionIdAndUserId(Long gameSessionId, Long userId);
}
