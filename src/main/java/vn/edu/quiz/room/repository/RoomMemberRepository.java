package vn.edu.quiz.room.repository;

import vn.edu.quiz.room.entity.RoomMember;
import vn.edu.quiz.room.enums.MembershipStatus;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
    List<RoomMember> findByRoomIdAndStatus(Long roomId, MembershipStatus status);
}
