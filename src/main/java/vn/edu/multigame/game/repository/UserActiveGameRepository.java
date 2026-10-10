package vn.edu.multigame.game.repository;

import vn.edu.multigame.game.entity.UserActiveGame;

import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface UserActiveGameRepository extends JpaRepository<UserActiveGame, Long> {
    List<UserActiveGame> findByGameSessionId(Long gameSessionId);
}
