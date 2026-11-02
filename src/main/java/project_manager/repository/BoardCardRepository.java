package project_manager.repository;

import project_manager.domain.BoardCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BoardCardRepository extends JpaRepository<BoardCardEntity, String> {
    List<BoardCardEntity> findByProjectIdOrderByPositionAsc(String projectId);
    List<BoardCardEntity> findByProjectIdAndColumnKeyOrderByPositionAsc(String projectId, String columnKey);
    long countByProjectId(String projectId);

    @Modifying
    @Query("update BoardCardEntity card set card.version = card.version + 1 "
        + "where card.id = :id and card.version = :version")
    int incrementVersionIfCurrent(@Param("id") String id, @Param("version") long version);

    @Query("select card.projectId, count(card) from BoardCardEntity card "
        + "where card.projectId in :projectIds group by card.projectId")
    List<Object[]> countByProjectIds(@Param("projectIds") List<String> projectIds);
}
