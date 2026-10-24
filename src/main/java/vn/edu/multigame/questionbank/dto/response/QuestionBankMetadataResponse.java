package vn.edu.multigame.questionbank.dto.response;

import vn.edu.multigame.questionbank.enums.Visibility;

/** Public metadata has no question content, options, image references or answer fields. */
public record QuestionBankMetadataResponse(long id, long ownerUserId, String title, Visibility visibility,
        int questionCount, long createdAtMs, long revision, vn.edu.multigame.questionbank.enums.GameMode mode, long subjectId, String subjectCode, String subjectName) {}
