package vn.edu.multigame.auth.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.room.enums.*;

/** Pure policy, deliberately independent of game lifecycle, resources and timers. */
@Component
public class PermissionPolicy {
    public enum RoomAction { VIEW, CONFIGURE, START }
    public enum GameAction { VIEW, CANCEL, ANSWER, SPIN, STAR }
    public record RoomContext(RoomStatus status, long hostUserId, boolean joined) {}
    public record GameContext(GameStatus status, Phase phase, long hostUserId, boolean member,
            Participation participation, PlayerState playerState) {}

    public void requireRoom(Long userId, RoomContext room, RoomAction action) {
        authenticated(userId);
        if (!room.joined()) throw forbidden();
        if (action == RoomAction.VIEW) return;
        if (userId != room.hostUserId()) throw forbidden();
        boolean valid = action == RoomAction.START ? room.status() == RoomStatus.WAITING
                : room.status() == RoomStatus.DRAFT || room.status() == RoomStatus.WAITING;
        if (!valid) throw invalidState();
    }
    public void requireGame(Long userId, GameContext game, GameAction action) {
        authenticated(userId);
        if (!game.member()) throw forbidden();
        if (action == GameAction.VIEW) return;
        if (action == GameAction.CANCEL) {
            if (userId != game.hostUserId()) throw forbidden();
            if (game.status() != GameStatus.ACTIVE) throw invalidState();
            return;
        }
        if (game.participation() != Participation.PLAYER || game.playerState() != PlayerState.PLAYING) throw forbidden();
        Phase required = action == GameAction.ANSWER ? Phase.QUESTION_OPEN : Phase.DECISION;
        if (game.status() != GameStatus.ACTIVE || game.phase() != required) throw invalidState();
    }
    private void authenticated(Long userId) { if (userId == null || userId <= 0) throw AuthService.unauthorized(); }
    private AuthFailure forbidden() { return new AuthFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền thực hiện thao tác."); }
    private AuthFailure invalidState() { return new AuthFailure(HttpStatus.CONFLICT, "INVALID_STATE", "Trạng thái hiện tại không cho phép thao tác."); }
}
