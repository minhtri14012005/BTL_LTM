package vn.edu.quiz.quiz.dto.response;

import java.util.List;
import vn.edu.quiz.quiz.enums.Visibility;

public record QuizOwnerResponse(long id, long ownerUserId, String title, Visibility visibility,
        int questionCount, long createdAtMs, long revision, List<QuestionResponse> questions) {}
