package vn.edu.quiz.game.service;
import vn.edu.quiz.game.dto.response.GameSnapshot;
/** Domain event after commit, except explicit runtime-error notifications which claim no persisted success. */
public record GameLifecycleEvent(String type, GameSnapshot snapshot,
        java.util.Map<Long,GameSnapshot.Player> players,vn.edu.quiz.room.dto.response.RoomResponse terminalRoom) {
    public GameLifecycleEvent { players=java.util.Map.copyOf(players); }
    public GameLifecycleEvent(String type,GameSnapshot snapshot) { this(type,snapshot,java.util.Map.of(),null); }
}
