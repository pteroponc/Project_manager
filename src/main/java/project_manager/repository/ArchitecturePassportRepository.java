package project_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project_manager.domain.ArchitecturePassportEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ArchitecturePassportRepository extends JpaRepository<ArchitecturePassportEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ArchitecturePassportEntity p where p.projectId=:id")
    Optional<ArchitecturePassportEntity> findForUpdate(@Param("id") String id);

    @Modifying
    @Query("update ArchitecturePassportEntity p set p.version=p.version+1 where p.projectId=:id and p.version=:version")
    int incrementVersionIfCurrent(@Param("id") String id, @Param("version") long version);

    @Query("select p.version from ArchitecturePassportEntity p where p.projectId=:id")
    Long currentVersion(@Param("id") String id);
}
