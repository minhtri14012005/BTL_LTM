package vn.edu.multigame.auth.security;

import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.multigame.auth.security.PermissionPolicy.*;
import vn.edu.multigame.auth.service.AuthFailure;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.room.enums.*;
import static org.assertj.core.api.Assertions.*;

/** Pure contexts are policy evidence only, not a running game engine. */
class PermissionPolicyTest {
    private final PermissionPolicy policy = new PermissionPolicy();
    private GameContext game(Phase phase, boolean member, Participation role, PlayerState state) {
        return new GameContext(GameStatus.ACTIVE, phase, 1L, member, role, state);
    }
    @ParameterizedTest @EnumSource(Phase.class)
    void eliminatedHostCanCancelInEveryActivePhase(Phase phase) {
        assertThatCode(() -> policy.requireGame(1L, game(phase, true, Participation.PLAYER, PlayerState.ELIMINATED), GameAction.CANCEL))
                .doesNotThrowAnyException();
    }
    @Test void spectatorHostCanCancelAndView() {
        var context = game(Phase.DECISION, true, Participation.SPECTATOR, null);
        policy.requireGame(1L, context, GameAction.CANCEL);
        policy.requireGame(1L, context, GameAction.VIEW);
    }
    static Stream<Arguments> forbiddenActions() {
        return Stream.of(GameAction.ANSWER, GameAction.SPIN, GameAction.STAR).flatMap(action -> Stream.of(
                Arguments.of(action, Participation.PLAYER, PlayerState.ELIMINATED),
                Arguments.of(action, Participation.SPECTATOR, null),
                Arguments.of(action, Participation.PLAYER, null)));
    }
    @ParameterizedTest @MethodSource("forbiddenActions")
    void eliminatedSpectatorOrMissingPlayerCannotPlay(GameAction action, Participation participation, PlayerState state) {
        denied(() -> policy.requireGame(1L, game(Phase.DECISION, true, participation, state), action), "FORBIDDEN");
    }
    @ParameterizedTest @EnumSource(GameAction.class)
    void outsiderCannotAccessGame(GameAction action) {
        denied(() -> policy.requireGame(1L, game(Phase.QUESTION_OPEN, false, Participation.PLAYER, PlayerState.PLAYING), action), "FORBIDDEN");
    }
    @Test void phaseAndTerminalStateAreChecked() {
        policy.requireGame(2L, game(Phase.QUESTION_OPEN, true, Participation.PLAYER, PlayerState.PLAYING), GameAction.ANSWER);
        for (GameAction action : new GameAction[] {GameAction.SPIN, GameAction.STAR}) {
            policy.requireGame(2L, game(Phase.DECISION, true, Participation.PLAYER, PlayerState.PLAYING), action);
            denied(() -> policy.requireGame(2L, game(Phase.QUESTION_OPEN, true, Participation.PLAYER, PlayerState.PLAYING), action), "INVALID_STATE");
        }
        denied(() -> policy.requireGame(2L, game(Phase.DECISION, true, Participation.PLAYER, PlayerState.PLAYING), GameAction.ANSWER), "INVALID_STATE");
        var finished = new GameContext(GameStatus.FINISHED, Phase.FINISHED, 1, true, Participation.PLAYER, PlayerState.ELIMINATED);
        policy.requireGame(1L, finished, GameAction.VIEW);
        denied(() -> policy.requireGame(1L, finished, GameAction.CANCEL), "INVALID_STATE");
    }
    @Test void hostIsRoomScopedAndLoginIsRequired() {
        denied(() -> policy.requireGame(2L, game(Phase.DECISION, true, Participation.PLAYER, PlayerState.PLAYING), GameAction.CANCEL), "FORBIDDEN");
        denied(() -> policy.requireGame(null, game(Phase.DECISION, true, Participation.SPECTATOR, null), GameAction.VIEW), "UNAUTHENTICATED");
        denied(() -> policy.requireGame(0L, game(Phase.DECISION, true, Participation.SPECTATOR, null), GameAction.VIEW), "UNAUTHENTICATED");
    }
    @ParameterizedTest @EnumSource(RoomAction.class)
    void leftOrOutsideRoomHasNoAccess(RoomAction action) {
        denied(() -> policy.requireRoom(1L, new RoomContext(RoomStatus.WAITING, 1, false), action), "FORBIDDEN");
    }
    @Test void onlyHostCanConfigureOrStartWaitingRoom() {
        var waiting = new RoomContext(RoomStatus.WAITING, 1, true);
        policy.requireRoom(2L, waiting, RoomAction.VIEW);
        policy.requireRoom(1L, waiting, RoomAction.CONFIGURE);
        policy.requireRoom(1L, waiting, RoomAction.START);
        denied(() -> policy.requireRoom(2L, waiting, RoomAction.CONFIGURE), "FORBIDDEN");
        denied(() -> policy.requireRoom(2L, waiting, RoomAction.START), "FORBIDDEN");
        denied(() -> policy.requireRoom(1L, new RoomContext(RoomStatus.ACTIVE, 1, true), RoomAction.CONFIGURE), "INVALID_STATE");
        denied(() -> policy.requireRoom(1L, new RoomContext(RoomStatus.DRAFT, 1, true), RoomAction.START), "INVALID_STATE");
    }
    private void denied(ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
