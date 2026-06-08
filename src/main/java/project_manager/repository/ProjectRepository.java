package project_manager.repository;

import project_manager.domain.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    Optional<ProjectEntity> findByName(String name);
    List<ProjectEntity> findByQuarterIgnoreCase(String quarter);
    List<ProjectEntity> findByHealthIgnoreCase(String health);
    List<ProjectEntity> findByQuarterIgnoreCaseAndHealthIgnoreCase(String quarter, String health);
}
