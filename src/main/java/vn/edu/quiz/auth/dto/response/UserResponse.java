package vn.edu.quiz.auth.dto.response;

import vn.edu.quiz.user.entity.UserAccount;

public record UserResponse(long id, String username, String displayName) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
