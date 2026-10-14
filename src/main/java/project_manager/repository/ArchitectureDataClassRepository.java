package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitectureDataClassEntity;
import project_manager.domain.ArchitectureDataClassId;

public interface ArchitectureDataClassRepository extends JpaRepository<ArchitectureDataClassEntity, ArchitectureDataClassId> {
}
