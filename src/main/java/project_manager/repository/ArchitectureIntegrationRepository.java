package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitectureIntegrationEntity;

public interface ArchitectureIntegrationRepository extends JpaRepository<ArchitectureIntegrationEntity, String> {
}
