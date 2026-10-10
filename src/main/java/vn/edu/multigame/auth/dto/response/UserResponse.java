package vn.edu.multigame.auth.dto.response;

import vn.edu.multigame.user.entity.UserAccount;

public record UserResponse(long id, String username, String displayName) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
