package vn.edu.quiz.game.entity;

import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.game.enums.GameStatus;
import vn.edu.quiz.game.enums.Phase;
import vn.edu.quiz.game.enums.EndReason;

import jakarta.persistence.*;
import vn.edu.quiz.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "game_session")
public class GameSession extends IdentityEntity {
    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private Long quizId;

    @Column(name = "quiz_author_user_id", nullable = false, updatable = false)
    private Long quizAuthorUserId;

    @Column(name = "quiz_title_snapshot", nullable = false, updatable = false, length = 200)
    private String quizTitleSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "phase", nullable = false, length = 24)
    private Phase phase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "end_reason", length = 24)
    private EndReason endReason;

    @Column(name = "question_count", nullable = false, updatable = false)
    private Integer questionCount;

    @Column(name = "current_question_index", nullable = false)
    private Integer currentQuestionIndex = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", nullable = false, updatable = false)
    private GameplayRulesSnapshot configSnapshot;

    @Column(name = "started_at_ms", nullable = false)
    private Long startedAtMs;

    @Column(name = "finished_at_ms")
    private Long finishedAtMs;

    @Column(name = "phase_opened_at_ms")
    private Long phaseOpenedAtMs;

    @Column(name = "phase_deadline_at_ms")
    private Long phaseDeadlineAtMs;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 0L;

    public GameSession() {}

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long value) { this.roomId = value; }

    public Long getQuizId() { return quizId; }
    public void setQuizId(Long value) { this.quizId = value; }

    public Long getQuizAuthorUserId() { return quizAuthorUserId; }
    public void setQuizAuthorUserId(Long value) { this.quizAuthorUserId = value; }

    public String getQuizTitleSnapshot() { return quizTitleSnapshot; }
    public void setQuizTitleSnapshot(String value) { this.quizTitleSnapshot = value; }

    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus value) { this.status = value; }

    public Phase getPhase() { return phase; }
    public void setPhase(Phase value) { this.phase = value; }

    public EndReason getEndReason() { return endReason; }
    public void setEndReason(EndReason value) { this.endReason = value; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer value) { this.questionCount = value; }

    public Integer getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(Integer value) { this.currentQuestionIndex = value; }

    public GameplayRulesSnapshot getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(GameplayRulesSnapshot value) { this.configSnapshot = value; }

    public Long getStartedAtMs() { return startedAtMs; }
    public void setStartedAtMs(Long value) { this.startedAtMs = value; }

    public Long getFinishedAtMs() { return finishedAtMs; }
    public void setFinishedAtMs(Long value) { this.finishedAtMs = value; }

    public Long getPhaseOpenedAtMs() { return phaseOpenedAtMs; }
    public void setPhaseOpenedAtMs(Long value) { this.phaseOpenedAtMs = value; }

    public Long getPhaseDeadlineAtMs() { return phaseDeadlineAtMs; }
    public void setPhaseDeadlineAtMs(Long value) { this.phaseDeadlineAtMs = value; }

    public Long getRevision() { return revision; }
    public void setRevision(Long value) { this.revision = value; }

}
