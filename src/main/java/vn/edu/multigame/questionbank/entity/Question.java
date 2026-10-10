package vn.edu.multigame.questionbank.entity;

import vn.edu.multigame.quiz.enums.Option;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "question")
public class Question extends IdentityEntity {
    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "content", nullable = false, length = 16, columnDefinition = "text")
    private String content;

    @Column(name = "option_a", length = 16, columnDefinition = "text")
    private String optionA;

    @Column(name = "option_b", length = 16, columnDefinition = "text")
    private String optionB;

    @Column(name = "option_c", length = 16, columnDefinition = "text")
    private String optionC;

    @Column(name = "option_d", length = 16, columnDefinition = "text")
    private String optionD;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "correct_option", length = 1)
    private Option correctOption;

    @Column(name = "image_ref", length = 71)
    private String imageRef;

    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs;

    @Column(name = "deleted_at_ms")
    private Long deletedAtMs;

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

    public Question() {}

    public Long getQuizId() { return quizId; }
    public void setQuizId(Long value) { this.quizId = value; }

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

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Option getCorrectOption() { return correctOption; }
    public void setCorrectOption(Option value) { this.correctOption = value; }

    public String getImageRef() { return imageRef; }
    public void setImageRef(String value) { this.imageRef = value; }

    public Long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(Long value) { this.createdAtMs = value; }

    public Long getDeletedAtMs() { return deletedAtMs; }
    public void setDeletedAtMs(Long value) { this.deletedAtMs = value; }


    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer value) { this.schemaVersion = value; }

    public vn.edu.multigame.game.enums.GameMode getMode() { return mode; }
    public void setMode(vn.edu.multigame.game.enums.GameMode value) { this.mode = value; }

    public java.util.Map<String,Object> getPayload() { return payload; }
    public void setPayload(java.util.Map<String,Object> value) { this.payload = value; }

}
