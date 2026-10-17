package vn.edu.multigame.room.repository;

import vn.edu.multigame.room.entity.RoomMember;
import vn.edu.multigame.room.enums.MembershipStatus;

import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="update room_member set status='LEFT',left_at_ms=greatest(joined_at_ms,:at) where id=:member and room_id=:room and user_id=:user and membership_generation=:generation and status='JOINED'",nativeQuery=true)
    int detachGeneration(@org.springframework.data.repository.query.Param("member") long member,@org.springframework.data.repository.query.Param("room") long room,
            @org.springframework.data.repository.query.Param("user") long user,@org.springframework.data.repository.query.Param("generation") long generation,@org.springframework.data.repository.query.Param("at") long at);
    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
    List<RoomMember> findByRoomIdAndStatus(Long roomId, MembershipStatus status);
}
