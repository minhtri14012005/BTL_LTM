package vn.edu.quiz.game.dto.response;

import java.util.List;
import java.util.Map;
import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.quiz.enums.Option;
import vn.edu.quiz.room.enums.Participation;

/** Typed snapshot; no future questions or other players' private decision/answer data. */
public record GameSnapshot(long gameSessionId, long roomId, long quizId, long quizAuthorUserId, String quizTitleSnapshot, GameStatus status, Phase phase,
        int questionIndex, int questionCount, long revision, long serverTimeMs,
        Long deadlineEpochMs, Long remainingMs, String runtimeState, boolean cleanupPending,
        EndReason endReason, List<Long> winners, GameplayRulesSnapshot config,
        List<Member> members, Question question, List<Result> results, Player player) {
    public record Member(long userId, String displayName, MemberRole role, Participation participation,
            PlayerState playerState, Integer score, Long totalAnswerTimeMs, Integer rank) {}
    public record Question(long id, String content, Map<Option,String> options, String imageRef, Option correctAnswer) {}
    public record Result(long userId, AnswerStatus outcome, int baseDelta, int scoreDelta, int scoreAfter,
            long answerTimeMs,long totalAnswerTimeMs,int winStreak,int loseStreak,
            boolean hasMomentumBefore,boolean hasRecoveryBefore,boolean hasMomentumAfter,boolean hasRecoveryAfter,
            boolean momentumConsumed,boolean recoveryConsumed,boolean momentumGranted,boolean recoveryGranted,
            boolean eliminatedNow,PlayerState playerState,Long eliminatedAtMs,Integer eliminatedQuestionIndex) {}
    public record Player(long userId, PlayerState state, int score, long totalAnswerTimeMs, int winStreak,
            int loseStreak, boolean momentum, boolean recovery, int remainingSpins, boolean starAvailable,
            List<SpinEffect> remainingSpinPool, SpinEffect currentSpin, boolean starSelected,
            boolean alreadyAnswered, Option selectedOption, Long eliminatedAtMs, Integer eliminatedQuestionIndex) {}

    public GameSnapshot contextual(long time, Long deadline, Long remaining, String runtime, boolean pending, Player self) {
        return new GameSnapshot(gameSessionId,roomId,quizId,quizAuthorUserId,quizTitleSnapshot,status,phase,questionIndex,questionCount,revision,time,
                deadline,remaining,runtime,pending,endReason,winners,config,members,question,results,self);
    }
    public GameSnapshot forPlayer(Player self) {
        return contextual(serverTimeMs,deadlineEpochMs,remainingMs,runtimeState,cleanupPending,self);
    }
    @com.fasterxml.jackson.annotation.JsonProperty("hasOfficialWinner")
    public boolean hasOfficialWinner() {
        return status==GameStatus.FINISHED && (endReason==EndReason.COMPLETED || endReason==EndReason.ONE_SURVIVOR || endReason==EndReason.ALL_ELIMINATED);
    }
}
