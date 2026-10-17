package vn.edu.multigame.user.entity;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;
import vn.edu.multigame.user.enums.UserRole;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "app_user")
public class UserAccount extends IdentityEntity {
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    public UserRole getRole() { return role; }
    public void setRole(UserRole value) { role = value; }

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

    @Column(name = "auth_epoch", nullable = false, updatable = false)
    private long authEpoch;
    public long getAuthEpoch() { return authEpoch; }

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
