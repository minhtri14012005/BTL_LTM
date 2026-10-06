package vn.edu.quiz.game.repository;

import vn.edu.quiz.game.entity.GameMember;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface GameMemberRepository extends JpaRepository<GameMember, Long> {
    List<GameMember> findByGameSessionId(Long gameSessionId);
    Optional<GameMember> findByGameSessionIdAndUserId(Long gameSessionId, Long userId);
}
