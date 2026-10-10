package vn.edu.multigame.room.repository;

import vn.edu.multigame.room.entity.RoomMember;
import vn.edu.multigame.room.enums.MembershipStatus;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
    List<RoomMember> findByRoomIdAndStatus(Long roomId, MembershipStatus status);
}
