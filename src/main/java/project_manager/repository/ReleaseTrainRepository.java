package project_manager.repository;

import project_manager.domain.ReleaseTrainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReleaseTrainRepository extends JpaRepository<ReleaseTrainEntity, String> {
    Optional<ReleaseTrainEntity> findByName(String name);
    List<ReleaseTrainEntity> findByQuarterIgnoreCase(String quarter);
    List<ReleaseTrainEntity> findByStatusIgnoreCase(String status);
    List<ReleaseTrainEntity> findByQuarterIgnoreCaseAndStatusIgnoreCase(String quarter, String status);
}
