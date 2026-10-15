package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitectureIntegrationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ArchitectureIntegrationRepository extends JpaRepository<ArchitectureIntegrationEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ArchitectureIntegrationEntity i where i.id=:id and i.passport.projectId=:projectId")
    Optional<ArchitectureIntegrationEntity> findForUpdate(@Param("id") String id, @Param("projectId") String projectId);

    @Modifying
    @Query("update ArchitectureIntegrationEntity i set i.version=i.version+1 where i.id=:id and i.version=:version")
    int incrementVersionIfCurrent(@Param("id") String id, @Param("version") long version);

    @Query("select i.version from ArchitectureIntegrationEntity i where i.id=:id and i.passport.projectId=:projectId")
    Long currentVersion(@Param("id") String id, @Param("projectId") String projectId);
}
