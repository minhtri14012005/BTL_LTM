package vn.edu.multigame.questionbank.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.multigame.questionbank.enums.Visibility;

@Component
public class QuestionBankAccessPolicy {
    public void requireUse(long userId, long ownerId, Visibility visibility) {
        if (visibility == Visibility.PRIVATE && userId != ownerId) throw QuestionBankFailure.notFound();
    }
    public void requireOwner(long userId, long ownerId) {
        if (userId != ownerId) throw QuestionBankFailure.forbidden();
    }
}
