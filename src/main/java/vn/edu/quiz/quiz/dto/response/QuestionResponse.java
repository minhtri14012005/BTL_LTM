package vn.edu.quiz.quiz.dto.response;

import java.util.Map;
import vn.edu.quiz.quiz.enums.Option;

/** Owner-only response, never used in public metadata/list mapping. */
public record QuestionResponse(long id, int position, String content, Map<Option, String> options,
        Option correctAnswer, String imageRef) {}
