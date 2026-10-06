package vn.edu.quiz.quiz.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.quiz.quiz.enums.Visibility;
import vn.edu.quiz.room.enums.Participation;

@Component
public class QuizAccessPolicy {
    public void requireUse(long userId, long ownerId, Visibility visibility) {
        if (visibility == Visibility.PRIVATE && userId != ownerId) throw QuizFailure.notFound();
    }
    public void requireOwner(long userId, long ownerId) {
        if (userId != ownerId) throw QuizFailure.forbidden();
    }
    /** Room/lifecycle must invoke for each participant; selecting a Quiz is separate from joining a Room. */
    public void requireParticipation(long userId, long authorId, Participation participation) {
        if (userId == authorId && participation == Participation.PLAYER) {
            throw new QuizFailure(HttpStatus.FORBIDDEN, "QUIZ_AUTHOR_CANNOT_PLAY", "Tác giả chỉ được quan sát Quiz của mình.");
        }
    }
}
