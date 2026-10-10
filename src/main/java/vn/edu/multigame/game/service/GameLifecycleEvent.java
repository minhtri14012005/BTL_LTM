package vn.edu.multigame.game.service;
import vn.edu.multigame.game.dto.response.GameSnapshot;
/** Domain event after commit, except explicit runtime-error notifications which claim no persisted success. */
public record GameLifecycleEvent(String type, GameSnapshot snapshot,
        java.util.Map<Long,GameSnapshot.Player> players,vn.edu.multigame.room.dto.response.RoomResponse terminalRoom) {
    public GameLifecycleEvent { players=java.util.Map.copyOf(players); }
    public GameLifecycleEvent(String type,GameSnapshot snapshot) { this(type,snapshot,java.util.Map.of(),null); }
}
