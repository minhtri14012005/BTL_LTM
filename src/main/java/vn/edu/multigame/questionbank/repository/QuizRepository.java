package vn.edu.multigame.questionbank.repository;

import vn.edu.multigame.questionbank.entity.Quiz;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByOwnerUserIdAndDeletedAtMsIsNull(Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quiz q where q.id = :id")
    Optional<Quiz> findLockedById(@Param("id") Long id);

    @Query("select q from Quiz q where q.deletedAtMs is null and (:mode is null or q.mode = :mode) and ((:scope = 'MINE' and q.ownerUserId = :userId) or (:scope = 'SHARED' and q.visibility = vn.edu.multigame.questionbank.enums.Visibility.PUBLIC) or (:scope = 'VISIBLE' and (q.ownerUserId = :userId or q.visibility = vn.edu.multigame.questionbank.enums.Visibility.PUBLIC))) order by q.id desc")
    Page<Quiz> findScoped(@Param("userId") Long userId,@Param("scope") String scope,
            @Param("mode") vn.edu.multigame.game.enums.GameMode mode,Pageable pageable);

    @Modifying
    @Query("update Quiz q set q.revision = q.revision + 1 where q.id = :id and q.revision = :revision")
    int touchRevision(@Param("id") Long id, @Param("revision") Long revision);
}
