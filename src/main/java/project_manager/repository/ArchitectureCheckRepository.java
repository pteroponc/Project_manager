package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitectureCheckEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ArchitectureCheckRepository extends JpaRepository<ArchitectureCheckEntity, String> {
    @Query("select count(c)>0 from ArchitectureCheckEntity c where c.passport.projectId=:projectId")
    boolean anyForProject(@Param("projectId") String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ArchitectureCheckEntity c where c.id=:id and c.passport.projectId=:projectId")
    Optional<ArchitectureCheckEntity> findForUpdate(@Param("id") String id, @Param("projectId") String projectId);

    @Modifying
    @Query("update ArchitectureCheckEntity c set c.version=c.version+1 where c.id=:id and c.version=:version")
    int incrementVersionIfCurrent(@Param("id") String id, @Param("version") long version);

    @Query("select c.version from ArchitectureCheckEntity c where c.id=:id and c.passport.projectId=:projectId")
    Long currentVersion(@Param("id") String id, @Param("projectId") String projectId);
}
