package vn.edu.multigame.questionbank.entity;

import vn.edu.multigame.questionbank.enums.Visibility;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", nullable = false, length = 24)
    private vn.edu.multigame.game.enums.GameMode mode = vn.edu.multigame.game.enums.GameMode.QUIZ;

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


    public vn.edu.multigame.game.enums.GameMode getMode() { return mode; }
    public void setMode(vn.edu.multigame.game.enums.GameMode value) { this.mode = value; }

}
