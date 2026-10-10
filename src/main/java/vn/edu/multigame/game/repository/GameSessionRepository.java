package vn.edu.multigame.game.repository;

import vn.edu.multigame.game.entity.GameSession;
import vn.edu.multigame.game.enums.GameStatus;

import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    @Query(value="select g from GameSession g where g.status = vn.edu.multigame.game.enums.GameStatus.FINISHED and exists (select m.gameSessionId from GameMember m where m.gameSessionId=g.id and m.userId=:userId) order by g.finishedAtMs desc,g.id desc",
        countQuery="select count(g) from GameSession g where g.status = vn.edu.multigame.game.enums.GameStatus.FINISHED and exists (select m.gameSessionId from GameMember m where m.gameSessionId=g.id and m.userId=:userId)")
    org.springframework.data.domain.Page<GameSession> history(@org.springframework.data.repository.query.Param("userId") long userId,org.springframework.data.domain.Pageable page);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameSession g where g.id = :id")
    Optional<GameSession> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    java.util.List<GameSession> findByStatus(GameStatus status);
    @Modifying
    @Query("update GameSession g set g.revision = g.revision + 1 where g.id = :id and g.revision = :revision")
    int touchRevision(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("revision") Long revision);
    Optional<GameSession> findByRoomIdAndStatus(Long roomId, GameStatus status);
}
