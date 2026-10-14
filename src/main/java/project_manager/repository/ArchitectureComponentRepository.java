package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitectureComponentEntity;

public interface ArchitectureComponentRepository extends JpaRepository<ArchitectureComponentEntity, String> {
}
