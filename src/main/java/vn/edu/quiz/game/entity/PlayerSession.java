package vn.edu.quiz.game.entity;

import vn.edu.quiz.room.enums.Participation;
import vn.edu.quiz.game.enums.PlayerState;
import vn.edu.quiz.game.enums.SpinEffect;

import jakarta.persistence.*;
import vn.edu.quiz.common.util.IdentityEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;

/** Scalar foreign keys: the migration enforces links; no cascading ORM deletes. */
@Entity
@Table(name = "player_session")
public class PlayerSession extends IdentityEntity {
    @Column(name = "game_session_id", nullable = false, updatable = false)
    private Long gameSessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "participation", nullable = false, updatable = false, length = 16)
    private Participation participation = Participation.PLAYER;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "player_state", nullable = false, length = 16)
    private PlayerState playerState = PlayerState.PLAYING;

    @Column(name = "score", nullable = false)
    private Integer score = 20;

    @Column(name = "total_answer_time_ms", nullable = false)
    private Long totalAnswerTimeMs = 0L;

    @Column(name = "win_streak", nullable = false)
    private Integer winStreak = 0;

    @Column(name = "lose_streak", nullable = false)
    private Integer loseStreak = 0;

    @Column(name = "has_momentum", nullable = false)
    private Boolean hasMomentum = false;

    @Column(name = "has_recovery", nullable = false)
    private Boolean hasRecovery = false;

    @Column(name = "remaining_spins", nullable = false)
    private Integer remainingSpins;

    @Column(name = "star_available", nullable = false)
    private Boolean starAvailable = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "remaining_spin_pool", nullable = false)
    private List<SpinEffect> remainingSpinPool;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "current_spin", length = 24)
    private SpinEffect currentSpin;

    @Column(name = "star_selected", nullable = false)
    private Boolean starSelected = false;

    @Column(name = "eliminated_at_ms")
    private Long eliminatedAtMs;

    @Column(name = "eliminated_question_index")
    private Integer eliminatedQuestionIndex;

    @Column(name = "final_rank")
    private Integer finalRank;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 0L;

    public PlayerSession() {}

    public Long getGameSessionId() { return gameSessionId; }
    public void setGameSessionId(Long value) { this.gameSessionId = value; }

    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }

    public Participation getParticipation() { return participation; }
    public void setParticipation(Participation value) { this.participation = value; }

    public PlayerState getPlayerState() { return playerState; }
    public void setPlayerState(PlayerState value) { this.playerState = value; }

    public Integer getScore() { return score; }
    public void setScore(Integer value) { this.score = value; }

    public Long getTotalAnswerTimeMs() { return totalAnswerTimeMs; }
    public void setTotalAnswerTimeMs(Long value) { this.totalAnswerTimeMs = value; }

    public Integer getWinStreak() { return winStreak; }
    public void setWinStreak(Integer value) { this.winStreak = value; }

    public Integer getLoseStreak() { return loseStreak; }
    public void setLoseStreak(Integer value) { this.loseStreak = value; }

    public Boolean getHasMomentum() { return hasMomentum; }
    public void setHasMomentum(Boolean value) { this.hasMomentum = value; }

    public Boolean getHasRecovery() { return hasRecovery; }
    public void setHasRecovery(Boolean value) { this.hasRecovery = value; }

    public Integer getRemainingSpins() { return remainingSpins; }
    public void setRemainingSpins(Integer value) { this.remainingSpins = value; }

    public Boolean getStarAvailable() { return starAvailable; }
    public void setStarAvailable(Boolean value) { this.starAvailable = value; }

    public List<SpinEffect> getRemainingSpinPool() { return remainingSpinPool; }
    public void setRemainingSpinPool(List<SpinEffect> value) { this.remainingSpinPool = value; }

    public SpinEffect getCurrentSpin() { return currentSpin; }
    public void setCurrentSpin(SpinEffect value) { this.currentSpin = value; }

    public Boolean getStarSelected() { return starSelected; }
    public void setStarSelected(Boolean value) { this.starSelected = value; }

    public Long getEliminatedAtMs() { return eliminatedAtMs; }
    public void setEliminatedAtMs(Long value) { this.eliminatedAtMs = value; }

    public Integer getEliminatedQuestionIndex() { return eliminatedQuestionIndex; }
    public void setEliminatedQuestionIndex(Integer value) { this.eliminatedQuestionIndex = value; }

    public Integer getFinalRank() { return finalRank; }
    public void setFinalRank(Integer value) { this.finalRank = value; }

    public Long getRevision() { return revision; }
    public void setRevision(Long value) { this.revision = value; }

}
