package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import project_manager.domain.ProjectMilestoneEntity;

import java.util.List;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestoneEntity, String> {
    List<ProjectMilestoneEntity> findByProject_IdOrderByPositionAscIdAsc(String projectId);
    long countByProject_Id(String projectId);
    @Query("""
        select milestone from ProjectMilestoneEntity milestone join fetch milestone.project project
        where milestone.completed = false and milestone.completedDate is null
          and milestone.plannedDate is not null and project.status in :statuses
        order by milestone.plannedDate, milestone.id
        """)
    List<ProjectMilestoneEntity> findUnfinishedByPlannedDate(@Param("statuses") List<String> statuses, Pageable page);
}
