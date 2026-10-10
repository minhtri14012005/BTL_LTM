package vn.edu.multigame.game.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.multigame.game.entity.GameStage;
public interface GameStageRepository extends JpaRepository<GameStage,Long> {
    List<GameStage> findByGameSessionIdOrderByOrderIndex(Long gameSessionId);
}
