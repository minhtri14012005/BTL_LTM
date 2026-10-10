package vn.edu.multigame.game.entity;

import vn.edu.multigame.game.enums.Phase;
import vn.edu.multigame.quiz.enums.Option;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "game_question")
public class GameQuestion extends IdentityEntity {
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private Long gameSessionId;

    @Column(name = "source_question_id", nullable = false, updatable = false)
    private Long sourceQuestionId;

    @Column(name = "order_index", nullable = false, updatable = false)
    private Integer orderIndex;

    @Column(name = "content", nullable = false, updatable = false, length = 16, columnDefinition = "text")
    private String content;

    @Column(name = "option_a", updatable = false, length = 16, columnDefinition = "text")
    private String optionA;

    @Column(name = "option_b", updatable = false, length = 16, columnDefinition = "text")
    private String optionB;

    @Column(name = "option_c", updatable = false, length = 16, columnDefinition = "text")
    private String optionC;

    @Column(name = "option_d", updatable = false, length = 16, columnDefinition = "text")
    private String optionD;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "correct_option", updatable = false, length = 1)
    private Option correctOption;

    @Column(name = "image_ref", updatable = false, length = 71)
    private String imageRef;

    @Column(name = "question_duration_ms", nullable = false, updatable = false)
    private Long questionDurationMs;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "phase", nullable = false, length = 24)
    private Phase phase;

    @Column(name = "opened_at_ms")
    private Long openedAtMs;

    @Column(name = "deadline_at_ms")
    private Long deadlineAtMs;

    @Column(name = "scored_at_ms")
    private Long scoredAtMs;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private Integer schemaVersion = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", nullable = false, length = 24)
    private vn.edu.multigame.game.enums.GameMode mode = vn.edu.multigame.game.enums.GameMode.QUIZ;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private java.util.Map<String,Object> payload;

    @Column(name = "stage_id", updatable = false)
    private Long stageId;

    @Column(name = "stage_question_index", updatable = false)
    private Integer stageQuestionIndex;

    @Column(name = "released_hint_count", nullable = false)
    private Integer releasedHintCount = 0;
    public Integer getReleasedHintCount() { return releasedHintCount; }
    public void setReleasedHintCount(Integer count) { releasedHintCount=count; }

    public GameQuestion() {}

    public Long getGameSessionId() { return gameSessionId; }
    public void setGameSessionId(Long value) { this.gameSessionId = value; }

    public Long getSourceQuestionId() { return sourceQuestionId; }
    public void setSourceQuestionId(Long value) { this.sourceQuestionId = value; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer value) { this.orderIndex = value; }

    public String getContent() { return content; }
    public void setContent(String value) { this.content = value; }

    public String getOptionA() { return optionA; }
    public void setOptionA(String value) { this.optionA = value; }

    public String getOptionB() { return optionB; }
    public void setOptionB(String value) { this.optionB = value; }

    public String getOptionC() { return optionC; }
    public void setOptionC(String value) { this.optionC = value; }

    public String getOptionD() { return optionD; }
    public void setOptionD(String value) { this.optionD = value; }

    public Option getCorrectOption() { return correctOption; }
    public void setCorrectOption(Option value) { this.correctOption = value; }

    public String getImageRef() { return imageRef; }
    public void setImageRef(String value) { this.imageRef = value; }

    public Long getQuestionDurationMs() { return questionDurationMs; }
    public void setQuestionDurationMs(Long value) { this.questionDurationMs = value; }

    public Phase getPhase() { return phase; }
    public void setPhase(Phase value) { this.phase = value; }

    public Long getOpenedAtMs() { return openedAtMs; }
    public void setOpenedAtMs(Long value) { this.openedAtMs = value; }

    public Long getDeadlineAtMs() { return deadlineAtMs; }
    public void setDeadlineAtMs(Long value) { this.deadlineAtMs = value; }

    public Long getScoredAtMs() { return scoredAtMs; }
    public void setScoredAtMs(Long value) { this.scoredAtMs = value; }


    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer value) { this.schemaVersion = value; }

    public vn.edu.multigame.game.enums.GameMode getMode() { return mode; }
    public void setMode(vn.edu.multigame.game.enums.GameMode value) { this.mode = value; }

    public java.util.Map<String,Object> getPayload() { return payload; }
    public void setPayload(java.util.Map<String,Object> value) { this.payload = value; }


    public Long getStageId() { return stageId; }
    public void setStageId(Long value) { this.stageId = value; }

    public Integer getStageQuestionIndex() { return stageQuestionIndex; }
    public void setStageQuestionIndex(Integer value) { this.stageQuestionIndex = value; }

}
