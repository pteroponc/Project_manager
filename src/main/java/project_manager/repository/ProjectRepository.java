package project_manager.repository;

import project_manager.domain.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    @Query("select p from ProjectEntity p where (:quarter = 'all' or lower(p.quarter) = lower(:quarter)) "
        + "and (:health = 'all' or lower(p.health) = lower(:health)) "
        + "and (:status = 'all' or lower(p.status) = lower(:status)) order by p.name, p.id")
    List<ProjectEntity> findFiltered(@Param("quarter") String quarter, @Param("health") String health,
                                    @Param("status") String status);

    @Query("select distinct p.quarter from ProjectEntity p order by p.quarter")
    List<String> findQuarters();

    @Query("select distinct p.status from ProjectEntity p order by p.status")
    List<String> findStatuses();

    @Query("select distinct p.health from ProjectEntity p order by p.health")
    List<String> findHealthValues();
    Optional<ProjectEntity> findByName(String name);
    List<ProjectEntity> findByQuarterIgnoreCase(String quarter);
    List<ProjectEntity> findByHealthIgnoreCase(String health);
    List<ProjectEntity> findByQuarterIgnoreCaseAndHealthIgnoreCase(String quarter, String health);
}
