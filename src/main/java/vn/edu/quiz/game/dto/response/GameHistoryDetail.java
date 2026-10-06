package vn.edu.quiz.game.dto.response;
import java.util.*;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.quiz.enums.Option;
public record GameHistoryDetail(long startedAtMs,long finishedAtMs,GameSnapshot finalSnapshot,List<Question> questions) {
    public record Question(long gameQuestionId,int questionIndex,String content,Map<Option,String> options,String imageRef,
            Option correctAnswer,long questionDurationMs,long openedAtMs,long deadlineAtMs,Long scoredAtMs,List<Answer> answers) {}
    public record Answer(long answerId,long userId,AnswerStatus answerStatus,Option selectedOption,Long receivedAtMs,
            long answerTimeMs,Long scoredAtMs,Integer baseDelta,Integer scoreDelta,Integer scoreAfter,
            SpinEffect spinEffect,Boolean starSelected,GameSnapshot.Result result) {}
}
