package project_manager.repository;

import project_manager.domain.BoardCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BoardCardRepository extends JpaRepository<BoardCardEntity, String> {
    List<BoardCardEntity> findByProjectIdOrderByPositionAsc(String projectId);
    List<BoardCardEntity> findByProjectIdAndColumnKeyOrderByPositionAsc(String projectId, String columnKey);
    long countByProjectId(String projectId);

    @Query("select card.projectId, count(card) from BoardCardEntity card "
        + "where card.projectId in :projectIds group by card.projectId")
    List<Object[]> countByProjectIds(@Param("projectIds") List<String> projectIds);
}
