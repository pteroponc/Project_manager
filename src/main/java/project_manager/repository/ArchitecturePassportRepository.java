package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitecturePassportEntity;

public interface ArchitecturePassportRepository extends JpaRepository<ArchitecturePassportEntity, String> {
}
