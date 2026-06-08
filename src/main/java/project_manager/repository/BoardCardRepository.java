package project_manager.repository;

import project_manager.domain.BoardCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardCardRepository extends JpaRepository<BoardCardEntity, String> {
    List<BoardCardEntity> findByProjectIdOrderByPositionAsc(String projectId);
    List<BoardCardEntity> findByProjectIdAndColumnKeyOrderByPositionAsc(String projectId, String columnKey);
}
