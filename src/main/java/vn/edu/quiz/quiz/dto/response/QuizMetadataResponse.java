package vn.edu.quiz.quiz.dto.response;

import vn.edu.quiz.quiz.enums.Visibility;

/** Public metadata has no question content, options, image references or answer fields. */
public record QuizMetadataResponse(long id, long ownerUserId, String title, Visibility visibility,
        int questionCount, long createdAtMs, long revision) {}
