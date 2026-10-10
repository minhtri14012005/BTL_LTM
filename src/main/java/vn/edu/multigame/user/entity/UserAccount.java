package vn.edu.multigame.user.entity;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "app_user")
public class UserAccount extends IdentityEntity {
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs;

    @Column(name = "deleted_at_ms")
    private Long deletedAtMs;

    public UserAccount() {}

    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { this.passwordHash = value; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { this.displayName = value; }

    public Long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(Long value) { this.createdAtMs = value; }

    public Long getDeletedAtMs() { return deletedAtMs; }
    public void setDeletedAtMs(Long value) { this.deletedAtMs = value; }

}
