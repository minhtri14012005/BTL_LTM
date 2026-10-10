package vn.edu.multigame.room.entity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.multigame.common.util.IdentityEntity;
import vn.edu.multigame.game.enums.GameMode;
/** Append-only Room configuration generations; game history uses its own frozen GameStage. */
@Entity @Table(name="room_stage")
public class RoomStage extends IdentityEntity {
    @Column(name="room_id",nullable=false,updatable=false) private Long roomId;
    @Column(name="plan_revision",nullable=false,updatable=false) private Long planRevision;
    @Column(name="order_index",nullable=false,updatable=false) private Integer orderIndex;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name="mode",nullable=false,length=24,updatable=false) private GameMode mode;
    @Column(name="quiz_id",nullable=false,updatable=false) private Long quizId;
    @Column(name="question_count",nullable=false,updatable=false) private Integer questionCount;
    @Column(name="question_duration_ms",nullable=false,updatable=false) private Long questionDurationMs;
    public Long getRoomId(){return roomId;} public void setRoomId(Long v){roomId=v;}
    public Long getPlanRevision(){return planRevision;} public void setPlanRevision(Long v){planRevision=v;}
    public Integer getOrderIndex(){return orderIndex;} public void setOrderIndex(Integer v){orderIndex=v;}
    public GameMode getMode(){return mode;} public void setMode(GameMode v){mode=v;}
    public Long getQuizId(){return quizId;} public void setQuizId(Long v){quizId=v;}
    public Integer getQuestionCount(){return questionCount;} public void setQuestionCount(Integer v){questionCount=v;}
    public Long getQuestionDurationMs(){return questionDurationMs;} public void setQuestionDurationMs(Long v){questionDurationMs=v;}
}
