package vn.edu.multigame.room.entity;

import vn.edu.multigame.room.enums.RoomStatus;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "room")
public class Room extends IdentityEntity {
    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Column(name = "quiz_id")
    private Long quizId;

    @Column(name = "room_code", nullable = false, length = 12)
    private String roomCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private RoomStatus status;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    @Column(name = "question_duration_ms")
    private Long questionDurationMs;

    @Column(name = "decision_duration_ms", nullable = false)
    private Long decisionDurationMs = 7000L;

    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 0L;

    @Column(name="config_version",nullable=false) private Integer configVersion=1;
    @Column(name="plan_revision",nullable=false) private Long planRevision=0L;
    public Integer getConfigVersion(){return configVersion;} public void setConfigVersion(Integer v){configVersion=v;}
    public Long getPlanRevision(){return planRevision;} public void setPlanRevision(Long v){planRevision=v;}

    public Room() {}

    public Long getHostUserId() { return hostUserId; }
    public void setHostUserId(Long value) { this.hostUserId = value; }

    public Long getQuizId() { return quizId; }
    public void setQuizId(Long value) { this.quizId = value; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String value) { this.roomCode = value; }

    public String getName() { return name; }
    public void setName(String value) { this.name = value; }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus value) { this.status = value; }

    public Integer getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(Integer value) { this.maxPlayers = value; }

    public Long getQuestionDurationMs() { return questionDurationMs; }
    public void setQuestionDurationMs(Long value) { this.questionDurationMs = value; }

    public Long getDecisionDurationMs() { return decisionDurationMs; }
    public void setDecisionDurationMs(Long value) { this.decisionDurationMs = value; }

    public Long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(Long value) { this.createdAtMs = value; }

    public Long getRevision() { return revision; }
    public void setRevision(Long value) { this.revision = value; }

}
