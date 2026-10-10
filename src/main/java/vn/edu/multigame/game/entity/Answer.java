package vn.edu.multigame.game.entity;

import vn.edu.multigame.game.enums.AnswerStatus;
import vn.edu.multigame.quiz.enums.Option;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "answer")
public class Answer extends IdentityEntity {
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private Long gameSessionId;

    @Column(name = "player_session_id", nullable = false, updatable = false)
    private Long playerSessionId;

    @Column(name = "game_question_id", nullable = false, updatable = false)
    private Long gameQuestionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "answer_status", nullable = false, length = 24)
    private AnswerStatus answerStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "selected_option", length = 1)
    private Option selectedOption;

    @Column(name = "received_at_ms")
    private Long receivedAtMs;

    @Column(name = "answer_time_ms", nullable = false)
    private Long answerTimeMs;

    @Column(name = "scored_at_ms")
    private Long scoredAtMs;

    @Column(name = "base_delta")
    private Integer baseDelta;

    @Column(name = "score_delta")
    private Integer scoreDelta;

    @Column(name = "score_after")
    private Integer scoreAfter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot")
    private Map<String, Object> resultSnapshot;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private Integer schemaVersion = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", nullable = false, length = 24)
    private vn.edu.multigame.game.enums.GameMode mode = vn.edu.multigame.game.enums.GameMode.QUIZ;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "answer_kind", nullable = false, length = 16)
    private vn.edu.multigame.game.enums.AnswerKind answerKind = vn.edu.multigame.game.enums.AnswerKind.OPTION;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_payload")
    private vn.edu.multigame.game.dto.TypedAnswer answerPayload;

    public Answer() {}

    public Long getGameSessionId() { return gameSessionId; }
    public void setGameSessionId(Long value) { this.gameSessionId = value; }

    public Long getPlayerSessionId() { return playerSessionId; }
    public void setPlayerSessionId(Long value) { this.playerSessionId = value; }

    public Long getGameQuestionId() { return gameQuestionId; }
    public void setGameQuestionId(Long value) { this.gameQuestionId = value; }

    public AnswerStatus getAnswerStatus() { return answerStatus; }
    public void setAnswerStatus(AnswerStatus value) { this.answerStatus = value; }

    public Option getSelectedOption() { return selectedOption; }
    public void setSelectedOption(Option value) { this.selectedOption = value; }

    public Long getReceivedAtMs() { return receivedAtMs; }
    public void setReceivedAtMs(Long value) { this.receivedAtMs = value; }

    public Long getAnswerTimeMs() { return answerTimeMs; }
    public void setAnswerTimeMs(Long value) { this.answerTimeMs = value; }

    public Long getScoredAtMs() { return scoredAtMs; }
    public void setScoredAtMs(Long value) { this.scoredAtMs = value; }

    public Integer getBaseDelta() { return baseDelta; }
    public void setBaseDelta(Integer value) { this.baseDelta = value; }

    public Integer getScoreDelta() { return scoreDelta; }
    public void setScoreDelta(Integer value) { this.scoreDelta = value; }

    public Integer getScoreAfter() { return scoreAfter; }
    public void setScoreAfter(Integer value) { this.scoreAfter = value; }

    public Map<String, Object> getResultSnapshot() { return resultSnapshot; }
    public void setResultSnapshot(Map<String, Object> value) { this.resultSnapshot = value; }


    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer value) { this.schemaVersion = value; }

    public vn.edu.multigame.game.enums.GameMode getMode() { return mode; }
    public void setMode(vn.edu.multigame.game.enums.GameMode value) { this.mode = value; }

    public vn.edu.multigame.game.enums.AnswerKind getAnswerKind() { return answerKind; }
    public void setAnswerKind(vn.edu.multigame.game.enums.AnswerKind value) { this.answerKind = value; }

    public vn.edu.multigame.game.dto.TypedAnswer getAnswerPayload() { return answerPayload; }
    public void setAnswerPayload(vn.edu.multigame.game.dto.TypedAnswer value) { this.answerPayload = value; }

}
