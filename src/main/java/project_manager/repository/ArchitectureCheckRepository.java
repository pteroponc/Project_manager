package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitectureCheckEntity;

public interface ArchitectureCheckRepository extends JpaRepository<ArchitectureCheckEntity, String> {
}
