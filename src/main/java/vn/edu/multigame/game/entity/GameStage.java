package vn.edu.multigame.game.entity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.multigame.common.util.IdentityEntity;
import vn.edu.multigame.game.enums.GameMode;
/** Immutable stage/config/source snapshot, with no runtime lifecycle in Task 16. */
@Entity
@Table(name="game_stage")
public class GameStage extends IdentityEntity {
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private Long gameSessionId;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private Integer schemaVersion = 2;

    @Column(name = "order_index", nullable = false, updatable = false)
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", nullable = false, length = 24)
    private GameMode mode;

    @Column(name = "source_quiz_id", nullable = false, updatable = false)
    private Long sourceQuizId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "title_snapshot", nullable = false, length = 200, updatable = false)
    private String titleSnapshot;

    @Column(name = "first_question_index", nullable = false, updatable = false)
    private Integer firstQuestionIndex;

    @Column(name = "question_count", nullable = false, updatable = false)
    private Integer questionCount;

    @Column(name = "question_duration_ms", nullable = false, updatable = false)
    private Long questionDurationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", nullable = false, updatable = false)
    private java.util.Map<String,Object> configSnapshot;

    public GameStage() {}

    public Long getGameSessionId() { return gameSessionId; }
    public void setGameSessionId(Long value) { this.gameSessionId = value; }

    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer value) { this.schemaVersion = value; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer value) { this.orderIndex = value; }

    public GameMode getMode() { return mode; }
    public void setMode(GameMode value) { this.mode = value; }

    public Long getSourceQuizId() { return sourceQuizId; }
    public void setSourceQuizId(Long value) { this.sourceQuizId = value; }

    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long value) { this.authorUserId = value; }

    public String getTitleSnapshot() { return titleSnapshot; }
    public void setTitleSnapshot(String value) { this.titleSnapshot = value; }

    public Integer getFirstQuestionIndex() { return firstQuestionIndex; }
    public void setFirstQuestionIndex(Integer value) { this.firstQuestionIndex = value; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer value) { this.questionCount = value; }

    public Long getQuestionDurationMs() { return questionDurationMs; }
    public void setQuestionDurationMs(Long value) { this.questionDurationMs = value; }

    public java.util.Map<String,Object> getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(java.util.Map<String,Object> value) { this.configSnapshot = value; }

}
