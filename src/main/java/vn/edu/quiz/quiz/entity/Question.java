package vn.edu.quiz.quiz.entity;

import vn.edu.quiz.quiz.enums.Option;

import jakarta.persistence.*;
import vn.edu.quiz.common.util.IdentityEntity;
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

    @Column(name = "option_a", nullable = false, length = 16, columnDefinition = "text")
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 16, columnDefinition = "text")
    private String optionB;

    @Column(name = "option_c", nullable = false, length = 16, columnDefinition = "text")
    private String optionC;

    @Column(name = "option_d", nullable = false, length = 16, columnDefinition = "text")
    private String optionD;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "correct_option", nullable = false, length = 1)
    private Option correctOption;

    @Column(name = "image_ref", length = 71)
    private String imageRef;

    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs;

    @Column(name = "deleted_at_ms")
    private Long deletedAtMs;

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

}
