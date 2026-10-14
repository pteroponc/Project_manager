package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitectureEnvironmentEntity;
import project_manager.domain.ArchitectureEnvironmentId;

public interface ArchitectureEnvironmentRepository extends JpaRepository<ArchitectureEnvironmentEntity, ArchitectureEnvironmentId> {
}
