package vn.edu.multigame.game.dto.response;
import java.util.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.quiz.enums.Option;
public record GameHistoryDetail(long startedAtMs,long finishedAtMs,GameSnapshot finalSnapshot,List<Question> questions) {
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
