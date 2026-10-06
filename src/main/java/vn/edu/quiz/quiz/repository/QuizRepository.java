package vn.edu.quiz.quiz.repository;

import vn.edu.quiz.quiz.entity.Quiz;

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

    @Query("select q from Quiz q where q.deletedAtMs is null and (q.ownerUserId = :userId or q.visibility = vn.edu.quiz.quiz.enums.Visibility.PUBLIC) order by q.id desc")
    Page<Quiz> findVisible(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("update Quiz q set q.revision = q.revision + 1 where q.id = :id and q.revision = :revision")
    int touchRevision(@Param("id") Long id, @Param("revision") Long revision);
}
