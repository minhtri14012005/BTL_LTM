package vn.edu.multigame.game.service;
import java.util.Map;
import vn.edu.multigame.game.dto.response.GameSnapshot;
/** Copied inside a transaction; becomes authoritative runtime data only after its proxy commits. */
public record CommittedGame(GameSnapshot publicView, Map<Long,GameSnapshot.Player> players,
        vn.edu.multigame.room.dto.response.RoomResponse terminalRoom) {
    public CommittedGame(GameSnapshot publicView,Map<Long,GameSnapshot.Player> players) { this(publicView,players,null); }
    public CommittedGame { players = Map.copyOf(players); }
    public boolean hasMember(long userId) { return publicView.members().stream().anyMatch(m -> m.userId()==userId); }
}
