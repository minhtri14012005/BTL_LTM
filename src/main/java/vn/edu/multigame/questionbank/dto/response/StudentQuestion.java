package vn.edu.multigame.questionbank.dto.response;

import java.util.Map;
import vn.edu.multigame.quiz.enums.Option;

/** Explicit allowlist for future authoritative question-open events. Never includes solutions or source IDs. */
public record StudentQuestion(int position,String content,Map<Option,String> options,String imageRef) {
    public static StudentQuestion from(QuestionResponse question) {
        return new StudentQuestion(question.position(),question.content(),question.options(),question.imageRef());
    }
}
