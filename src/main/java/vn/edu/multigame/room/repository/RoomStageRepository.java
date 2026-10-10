package vn.edu.multigame.room.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.multigame.room.entity.RoomStage;
public interface RoomStageRepository extends JpaRepository<RoomStage,Long> {
    List<RoomStage> findByRoomIdAndPlanRevisionOrderByOrderIndex(Long roomId,Long planRevision);
}
