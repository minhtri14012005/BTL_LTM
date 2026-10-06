package vn.edu.quiz.quiz.entity;

import vn.edu.quiz.quiz.enums.Visibility;

import jakarta.persistence.*;
import vn.edu.quiz.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "quiz")
public class Quiz extends IdentityEntity {
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "visibility", nullable = false, length = 16)
    private Visibility visibility;

    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs;

    @Column(name = "deleted_at_ms")
    private Long deletedAtMs;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 0L;

    public Quiz() {}

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { this.ownerUserId = value; }

    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility value) { this.visibility = value; }

    public Long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(Long value) { this.createdAtMs = value; }

    public Long getDeletedAtMs() { return deletedAtMs; }
    public void setDeletedAtMs(Long value) { this.deletedAtMs = value; }

    public Long getRevision() { return revision; }
    public void setRevision(Long value) { this.revision = value; }

}
