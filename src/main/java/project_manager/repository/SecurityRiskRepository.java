package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.SecurityRiskEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SecurityRiskRepository extends JpaRepository<SecurityRiskEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SecurityRiskEntity r where r.id=:id and r.passport.projectId=:projectId")
    Optional<SecurityRiskEntity> findForUpdate(@Param("id") String id, @Param("projectId") String projectId);

    @Modifying
    @Query("update SecurityRiskEntity r set r.version=r.version+1 where r.id=:id and r.version=:version")
    int incrementVersionIfCurrent(@Param("id") String id, @Param("version") long version);

    @Query("select r.version from SecurityRiskEntity r where r.id=:id and r.passport.projectId=:projectId")
    Long currentVersion(@Param("id") String id, @Param("projectId") String projectId);
}
