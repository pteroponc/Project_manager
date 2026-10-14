package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_architecture_environments")
@Check(name = "ck_arch_environments_environment_code", constraints = "environment_code IN ('DEV', 'TEST', 'PREPROD', 'PROD', 'OTHER')")
public class ArchitectureEnvironmentEntity {
    @EmbeddedId
    private ArchitectureEnvironmentId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arch_env_passport"))
    private ArchitecturePassportEntity passport;

    public ArchitectureEnvironmentId getId() { return id; }
    public void setId(ArchitectureEnvironmentId value) { id = value; }

    public ArchitecturePassportEntity getPassport() { return passport; }
    public void setPassport(ArchitecturePassportEntity value) { passport = value; }
}
