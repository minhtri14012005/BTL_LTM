package vn.edu.multigame.questionbank.dto.response;

import java.util.Map;
import vn.edu.multigame.quiz.enums.Option;

/** Owner-only response, never used in public metadata/list mapping. */
public record QuestionResponse(long id, int position, String content, Map<Option, String> options,
        Option correctAnswer, String imageRef, java.util.List<String> acceptedAnswers,
        java.util.List<ArrangementItemResponse> pieces,
        java.util.List<ArrangementItemResponse> items, java.util.List<String> correctOrder, String mediaRef, java.util.List<vn.edu.multigame.questionbank.dto.request.HintRequest> hints) {}
