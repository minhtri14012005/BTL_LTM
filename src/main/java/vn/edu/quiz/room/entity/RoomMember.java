package vn.edu.quiz.room.entity;

import vn.edu.quiz.room.enums.Participation;
import vn.edu.quiz.room.enums.MembershipStatus;

import jakarta.persistence.*;
import vn.edu.quiz.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "room_member")
public class RoomMember extends IdentityEntity {
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "participation", nullable = false, length = 16)
    private Participation participation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private MembershipStatus status;

    @Column(name = "joined_at_ms", nullable = false)
    private Long joinedAtMs;

    @Column(name = "left_at_ms")
    private Long leftAtMs;

    public RoomMember() {}

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long value) { this.roomId = value; }

    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }

    public Participation getParticipation() { return participation; }
    public void setParticipation(Participation value) { this.participation = value; }

    public MembershipStatus getStatus() { return status; }
    public void setStatus(MembershipStatus value) { this.status = value; }

    public Long getJoinedAtMs() { return joinedAtMs; }
    public void setJoinedAtMs(Long value) { this.joinedAtMs = value; }

    public Long getLeftAtMs() { return leftAtMs; }
    public void setLeftAtMs(Long value) { this.leftAtMs = value; }

    /** Reuse the row on rejoin; GameMember keeps the participation snapshot. */
    public void leave(long atMs) {
        if (status != MembershipStatus.JOINED || atMs < joinedAtMs) {
            throw new IllegalStateException("Membership cannot leave at this time");
        }
        status = MembershipStatus.LEFT;
        leftAtMs = atMs;
    }

    public void rejoin(long atMs) {
        if (status != MembershipStatus.LEFT || leftAtMs == null || atMs < leftAtMs) {
            throw new IllegalStateException("Membership cannot rejoin at this time");
        }
        status = MembershipStatus.JOINED;
        joinedAtMs = atMs;
        leftAtMs = null;
    }
}
