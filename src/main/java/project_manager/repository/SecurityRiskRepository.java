package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.SecurityRiskEntity;

public interface SecurityRiskRepository extends JpaRepository<SecurityRiskEntity, String> {
}
