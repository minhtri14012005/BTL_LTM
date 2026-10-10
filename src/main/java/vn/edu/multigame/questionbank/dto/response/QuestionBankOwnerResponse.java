package vn.edu.multigame.questionbank.dto.response;

import java.util.List;
import vn.edu.multigame.questionbank.enums.Visibility;

public record QuestionBankOwnerResponse(long id, long ownerUserId, String title, Visibility visibility,
        int questionCount, long createdAtMs, long revision, List<QuestionResponse> questions, vn.edu.multigame.game.enums.GameMode mode) {}
