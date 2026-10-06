package vn.edu.quiz.game.repository;

import vn.edu.quiz.game.entity.UserActiveGame;

import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface UserActiveGameRepository extends JpaRepository<UserActiveGame, Long> {
    List<UserActiveGame> findByGameSessionId(Long gameSessionId);
}
