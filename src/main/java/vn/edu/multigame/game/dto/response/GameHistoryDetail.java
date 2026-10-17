package vn.edu.multigame.game.dto.response;
import java.util.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.quiz.enums.Option;
public record GameHistoryDetail(long startedAtMs,long finishedAtMs,GameSnapshot finalSnapshot,List<Question> questions,List<Decision> decisions,List<Restriction> restrictions) {
    public GameHistoryDetail(long startedAtMs,long finishedAtMs,GameSnapshot finalSnapshot,List<Question> questions){this(startedAtMs,finishedAtMs,finalSnapshot,questions,List.of(),List.of());}
    public record Decision(long userId,int questionIndex,String action,SpinEffect spinEffect,int remainingSpins,boolean starAvailable,boolean starSelected,long committedAtMs) {}
    public record Restriction(long userId,String kind,long effectiveAtMs) {}
    public record Question(long gameQuestionId,int questionIndex,String content,Map<Option,String> options,String imageRef,
            Option correctAnswer,long questionDurationMs,long openedAtMs,long deadlineAtMs,Long scoredAtMs,List<Answer> answers,vn.edu.multigame.game.enums.GameMode mode,Integer stageQuestionIndex,Map<String,Object> payload) {
        public Question(long gameQuestionId,int questionIndex,String content,Map<Option,String> options,String imageRef,Option correctAnswer,long questionDurationMs,long openedAtMs,long deadlineAtMs,Long scoredAtMs,List<Answer> answers) {
            this(gameQuestionId,questionIndex,content,options,imageRef,correctAnswer,questionDurationMs,openedAtMs,deadlineAtMs,scoredAtMs,answers,vn.edu.multigame.game.enums.GameMode.QUIZ,null,null);
        }
    }
    public record Answer(long answerId,long userId,AnswerStatus answerStatus,Option selectedOption,Long receivedAtMs,
            long answerTimeMs,Long scoredAtMs,Integer baseDelta,Integer scoreDelta,Integer scoreAfter,
            SpinEffect spinEffect,Boolean starSelected,GameSnapshot.Result result,vn.edu.multigame.game.dto.TypedAnswer submittedAnswer) {
        public Answer(long answerId,long userId,AnswerStatus answerStatus,Option selectedOption,Long receivedAtMs,long answerTimeMs,Long scoredAtMs,Integer baseDelta,Integer scoreDelta,Integer scoreAfter,SpinEffect spinEffect,Boolean starSelected,GameSnapshot.Result result) {this(answerId,userId,answerStatus,selectedOption,receivedAtMs,answerTimeMs,scoredAtMs,baseDelta,scoreDelta,scoreAfter,spinEffect,starSelected,result,null);}
    }
}
