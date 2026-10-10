package vn.edu.multigame.questionbank.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.room.enums.Participation;

@Component
public class QuestionBankAccessPolicy {
    public void requireUse(long userId, long ownerId, Visibility visibility) {
        if (visibility == Visibility.PRIVATE && userId != ownerId) throw QuestionBankFailure.notFound();
    }
    public void requireOwner(long userId, long ownerId) {
        if (userId != ownerId) throw QuestionBankFailure.forbidden();
    }
    /** Room/lifecycle must invoke for each participant; selecting a Quiz is separate from joining a Room. */
    public void requireParticipation(long userId, long authorId, Participation participation) {
        if (userId == authorId && participation == Participation.PLAYER) {
            throw new QuestionBankFailure(HttpStatus.FORBIDDEN, "QUIZ_AUTHOR_CANNOT_PLAY", "Tác giả chỉ được quan sát Quiz của mình.");
        }
    }
}
