package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ProjectMilestoneEntity;

import java.util.List;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestoneEntity, String> {
    List<ProjectMilestoneEntity> findByProject_IdOrderByPositionAscIdAsc(String projectId);
    long countByProject_Id(String projectId);
}
