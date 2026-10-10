package vn.edu.multigame.game.dto.response;

import java.util.List;
import java.util.Map;
import vn.edu.multigame.game.dto.GameplayRulesSnapshot;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.quiz.enums.Option;
import vn.edu.multigame.room.enums.Participation;

/** Typed snapshot; no future questions or other players' private decision/answer data. */
public record GameSnapshot(long gameSessionId, long roomId, Long quizId, Long quizAuthorUserId, String quizTitleSnapshot, GameStatus status, Phase phase,
        int questionIndex, int questionCount, long revision, long serverTimeMs,
        Long deadlineEpochMs, Long remainingMs, String runtimeState, boolean cleanupPending,
        EndReason endReason, List<Long> winners, GameplayRulesSnapshot config,
        List<Member> members, Question question, List<Result> results, Player player,
        int schemaVersion, vn.edu.multigame.game.dto.MultimodeRulesSnapshot v2Config, List<Stage> stages, Integer stageIndex, Stage stage, List<Long> readyPlayers) {
    public record Stage(long id,int orderIndex,vn.edu.multigame.game.enums.GameMode mode,long sourceQuizId,long authorUserId,String title,int firstQuestionIndex,int questionCount,long questionDurationMs) {}
    public GameSnapshot(long gameSessionId,long roomId,Long quizId,Long quizAuthorUserId,String quizTitleSnapshot,GameStatus status,Phase phase,
            int questionIndex,int questionCount,long revision,long serverTimeMs,Long deadlineEpochMs,Long remainingMs,String runtimeState,boolean cleanupPending,
            EndReason endReason,List<Long> winners,GameplayRulesSnapshot config,List<Member> members,Question question,List<Result> results,Player player) {
        this(gameSessionId,roomId,quizId,quizAuthorUserId,quizTitleSnapshot,status,phase,questionIndex,questionCount,revision,serverTimeMs,deadlineEpochMs,remainingMs,runtimeState,cleanupPending,endReason,winners,config,members,question,results,player,1,null,List.of(),null,null,List.of());
    }
    public record Member(long userId, String displayName, MemberRole role, Participation participation,
            PlayerState playerState, Integer score, Long totalAnswerTimeMs, Integer rank,Long totalCorrectAnswerTimeMs) {
        public Member(long userId,String displayName,MemberRole role,Participation participation,PlayerState playerState,Integer score,Long totalAnswerTimeMs,Integer rank) {this(userId,displayName,role,participation,playerState,score,totalAnswerTimeMs,rank,null);}
    }
    public record Question(long id, String content, Map<Option,String> options, String imageRef, Option correctAnswer,vn.edu.multigame.game.enums.GameMode mode,Integer stageQuestionIndex,Map<String,Object> payload) {
        public Question(long id,String content,Map<Option,String> options,String imageRef,Option correctAnswer) {this(id,content,options,imageRef,correctAnswer,vn.edu.multigame.game.enums.GameMode.QUIZ,null,null);}
    }
    public record Result(long userId, AnswerStatus outcome, int baseDelta, int scoreDelta, int scoreAfter,
            long answerTimeMs,long totalAnswerTimeMs,int winStreak,int loseStreak,
            boolean hasMomentumBefore,boolean hasRecoveryBefore,boolean hasMomentumAfter,boolean hasRecoveryAfter,
            boolean momentumConsumed,boolean recoveryConsumed,boolean momentumGranted,boolean recoveryGranted,
            boolean eliminatedNow,PlayerState playerState,Long eliminatedAtMs,Integer eliminatedQuestionIndex,Long totalCorrectAnswerTimeMs,Integer ruleDelta) {
        public Result(long userId,AnswerStatus outcome,int baseDelta,int scoreDelta,int scoreAfter,long answerTimeMs,long totalAnswerTimeMs,int winStreak,int loseStreak,
                boolean hasMomentumBefore,boolean hasRecoveryBefore,boolean hasMomentumAfter,boolean hasRecoveryAfter,boolean momentumConsumed,boolean recoveryConsumed,boolean momentumGranted,boolean recoveryGranted,boolean eliminatedNow,PlayerState playerState,Long eliminatedAtMs,Integer eliminatedQuestionIndex) {
            this(userId,outcome,baseDelta,scoreDelta,scoreAfter,answerTimeMs,totalAnswerTimeMs,winStreak,loseStreak,hasMomentumBefore,hasRecoveryBefore,hasMomentumAfter,hasRecoveryAfter,momentumConsumed,recoveryConsumed,momentumGranted,recoveryGranted,eliminatedNow,playerState,eliminatedAtMs,eliminatedQuestionIndex,null,null);
        }
    }
    public record Player(long userId, PlayerState state, int score, long totalAnswerTimeMs, int winStreak,
            int loseStreak, boolean momentum, boolean recovery, int remainingSpins, boolean starAvailable,
            List<SpinEffect> remainingSpinPool, SpinEffect currentSpin, boolean starSelected,
            boolean alreadyAnswered, Option selectedOption, Long eliminatedAtMs, Integer eliminatedQuestionIndex,Long totalCorrectAnswerTimeMs,vn.edu.multigame.game.dto.TypedAnswer submittedAnswer) {
        public Player(long userId,PlayerState state,int score,long totalAnswerTimeMs,int winStreak,int loseStreak,boolean momentum,boolean recovery,int remainingSpins,boolean starAvailable,List<SpinEffect> remainingSpinPool,SpinEffect currentSpin,boolean starSelected,boolean alreadyAnswered,Option selectedOption,Long eliminatedAtMs,Integer eliminatedQuestionIndex) {
            this(userId,state,score,totalAnswerTimeMs,winStreak,loseStreak,momentum,recovery,remainingSpins,starAvailable,remainingSpinPool,currentSpin,starSelected,alreadyAnswered,selectedOption,eliminatedAtMs,eliminatedQuestionIndex,null,null);
        }
    }

    public GameSnapshot contextual(long time, Long deadline, Long remaining, String runtime, boolean pending, Player self) {
        return new GameSnapshot(gameSessionId,roomId,quizId,quizAuthorUserId,quizTitleSnapshot,status,phase,questionIndex,questionCount,revision,time,
                deadline,remaining,runtime,pending,endReason,winners,config,members,question,results,self,schemaVersion,v2Config,stages,stageIndex,stage,readyPlayers);
    }
    public GameSnapshot withReady(List<Long> ready) {
        return new GameSnapshot(gameSessionId,roomId,quizId,quizAuthorUserId,quizTitleSnapshot,status,phase,questionIndex,questionCount,revision,serverTimeMs,deadlineEpochMs,remainingMs,runtimeState,cleanupPending,endReason,winners,config,members,question,results,player,schemaVersion,v2Config,stages,stageIndex,stage,List.copyOf(ready));
    }
    public GameSnapshot forPlayer(Player self) {
        return contextual(serverTimeMs,deadlineEpochMs,remainingMs,runtimeState,cleanupPending,self);
    }
    @com.fasterxml.jackson.annotation.JsonProperty("hasOfficialWinner")
    public boolean hasOfficialWinner() {
        return status==GameStatus.FINISHED && (endReason==EndReason.COMPLETED || endReason==EndReason.ONE_SURVIVOR || endReason==EndReason.ALL_ELIMINATED);
    }
}
