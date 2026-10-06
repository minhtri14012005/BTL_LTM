package vn.edu.quiz.game.entity;

import vn.edu.quiz.room.enums.Participation;
import vn.edu.quiz.game.enums.MemberRole;

import jakarta.persistence.*;
import vn.edu.quiz.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "game_member")
public class GameMember extends IdentityEntity {
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private Long gameSessionId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, updatable = false, length = 16)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "participation", nullable = false, updatable = false, length = 16)
    private Participation participation;

    @Column(name = "display_name_snapshot", nullable = false, updatable = false, length = 100)
    private String displayNameSnapshot;

    public GameMember() {}

    public Long getGameSessionId() { return gameSessionId; }
    public void setGameSessionId(Long value) { this.gameSessionId = value; }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long value) { this.roomId = value; }

    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }

    public MemberRole getRole() { return role; }
    public void setRole(MemberRole value) { this.role = value; }

    public Participation getParticipation() { return participation; }
    public void setParticipation(Participation value) { this.participation = value; }

    public String getDisplayNameSnapshot() { return displayNameSnapshot; }
    public void setDisplayNameSnapshot(String value) { this.displayNameSnapshot = value; }

}
