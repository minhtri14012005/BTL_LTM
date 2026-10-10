package vn.edu.multigame.room.repository;

import vn.edu.multigame.room.entity.Room;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.domain.*;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findLockedById(@Param("id") Long id);

    @Query("select r from Room r where exists (select m.id from RoomMember m where m.roomId = r.id and m.userId = :userId and m.status = vn.edu.multigame.room.enums.MembershipStatus.JOINED) order by r.id desc")
    Page<Room> findJoined(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("update Room r set r.revision = r.revision + 1 where r.id = :id and r.revision = :revision")
    int touchRevision(@Param("id") Long id, @Param("revision") Long revision);
}
