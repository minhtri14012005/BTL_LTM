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

    /** Read-only media permission projection, not a gameplay/lifecycle operation. */
    @Query(value = """
        select count(*) from game_session gs
        join game_member gm on gm.game_session_id = gs.id and gm.user_id = :userId
        join game_question gq on gq.game_session_id = gs.id
        where gs.id = :gameId and ((gq.stage_id is null and gs.quiz_id = :quizId) or exists (select 1 from game_stage stage where stage.id=gq.stage_id and stage.game_session_id=gs.id and stage.source_quiz_id=:quizId)) and ((gq.mode='IMAGE_WORD' and JSON_UNQUOTE(JSON_EXTRACT(gq.payload,'$.mediaRef'))=:imageRef) or (gq.mode='QUIZ' and gq.image_ref=:imageRef))
          and gq.opened_at_ms is not null
          and gq.phase in ('QUESTION_OPEN','QUESTION_CLOSED','SCORING','RESULT','FINISHED')
        """, nativeQuery = true)
    long countReleasedImageForMember(@Param("quizId") long quizId, @Param("gameId") long gameId,
            @Param("userId") long userId, @Param("imageRef") String imageRef);
    @Query(value="""
        select count(*) from game_session gs
        join game_member gm on gm.game_session_id=gs.id and gm.user_id=:userId
        join game_question gq on gq.game_session_id=gs.id
        join game_stage stage on stage.id=gq.stage_id and stage.game_session_id=gs.id and stage.source_quiz_id=:quizId
        where gs.id=:gameId and gq.mode='SONG' and JSON_UNQUOTE(JSON_EXTRACT(gq.payload,'$.mediaRef'))=:mediaRef
          and gq.opened_at_ms is not null and gq.phase in ('QUESTION_OPEN','QUESTION_CLOSED','SCORING','RESULT','FINISHED')
        """,nativeQuery=true)
    long countReleasedVideoForMember(@Param("quizId") long quizId,@Param("gameId") long gameId,@Param("userId") long userId,@Param("mediaRef") String mediaRef);
}
