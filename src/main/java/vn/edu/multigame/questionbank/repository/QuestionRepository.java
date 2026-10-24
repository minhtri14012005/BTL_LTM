package vn.edu.multigame.questionbank.repository;

import vn.edu.multigame.questionbank.entity.Question;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuizIdAndDeletedAtMsIsNullOrderByOrderIndex(Long quizId);

    long countByQuizIdAndDeletedAtMsIsNull(Long quizId);

    @Query("select coalesce(max(q.orderIndex), 0) from Question q where q.quizId = :quizId")
    int maximumOrderIndex(@Param("quizId") Long quizId);

}
