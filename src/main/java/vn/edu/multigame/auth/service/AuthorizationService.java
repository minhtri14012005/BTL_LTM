package vn.edu.multigame.auth.service;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.auth.security.PermissionPolicy;
import vn.edu.multigame.auth.security.PermissionPolicy.*;
import vn.edu.multigame.game.repository.*;
import vn.edu.multigame.room.repository.*;
import vn.edu.multigame.room.enums.MembershipStatus;

/** Repository adapter for future use-cases. No Room/Game endpoint is exposed by Task 3. */
@Service
@Profile("mysql")
@Transactional(readOnly = true)
public class AuthorizationService {
    private final AuthService users;
    private final PermissionPolicy policy;
    private final RoomRepository rooms;
    private final RoomMemberRepository roomMembers;
    private final GameSessionRepository games;
    private final GameMemberRepository gameMembers;
    private final PlayerSessionRepository players;
    public AuthorizationService(AuthService users, PermissionPolicy policy, RoomRepository rooms,
            RoomMemberRepository roomMembers, GameSessionRepository games, GameMemberRepository gameMembers,
            PlayerSessionRepository players) {
        this.users = users; this.policy = policy; this.rooms = rooms; this.roomMembers = roomMembers;
        this.games = games; this.gameMembers = gameMembers; this.players = players;
    }
    public void requireRoom(Authentication authentication, long roomId, RoomAction action) {
        long userId = users.requireActiveUser(authentication).getId();
        var room = rooms.findById(roomId).orElseThrow(this::notFound);
        boolean joined = roomMembers.findByRoomIdAndUserId(roomId, userId)
                .filter(member -> member.getStatus() == MembershipStatus.JOINED).isPresent();
        policy.requireRoom(userId, new RoomContext(room.getStatus(), room.getHostUserId(), joined), action);
    }
    public void requireGame(Authentication authentication, long gameId, GameAction action) {
        long userId = users.requireActiveUser(authentication).getId();
        var game = games.findById(gameId).orElseThrow(this::notFound);
        var room = rooms.findById(game.getRoomId()).orElseThrow(this::notFound);
        var member = gameMembers.findByGameSessionIdAndUserId(gameId, userId);
        var player = players.findByGameSessionIdAndUserId(gameId, userId);
        policy.requireGame(userId, new GameContext(game.getStatus(), game.getPhase(), room.getHostUserId(),
                member.isPresent(), member.map(m -> m.getParticipation()).orElse(null),
                player.map(p -> p.getPlayerState()).orElse(null)), action);
    }
    private AuthFailure notFound() { return new AuthFailure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy Room/Game."); }
}
