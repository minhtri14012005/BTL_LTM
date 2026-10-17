package vn.edu.multigame.auth.dto.response;

import vn.edu.multigame.user.entity.UserAccount;
import vn.edu.multigame.user.enums.UserRole;

public record UserResponse(long id, String username, String displayName, UserRole role) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
