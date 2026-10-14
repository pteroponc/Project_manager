package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.IntegrationDataClassEntity;
import project_manager.domain.IntegrationDataClassId;

public interface IntegrationDataClassRepository extends JpaRepository<IntegrationDataClassEntity, IntegrationDataClassId> {
}
