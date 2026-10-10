package vn.edu.multigame.game.entity;

import vn.edu.multigame.game.enums.GameStatus;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "user_active_game")
public class UserActiveGame {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_session_id", nullable = false)
    private Long gameSessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "game_status", nullable = false, length = 16)
    private GameStatus gameStatus = GameStatus.ACTIVE;

    public UserActiveGame() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }

    public Long getGameSessionId() { return gameSessionId; }
    public void setGameSessionId(Long value) { this.gameSessionId = value; }

    public GameStatus getGameStatus() { return gameStatus; }
    public void setGameStatus(GameStatus value) { this.gameStatus = value; }

}
