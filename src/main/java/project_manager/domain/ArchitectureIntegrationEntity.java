package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_architecture_integrations",
    indexes = {@Index(name = "idx_arch_integration_project", columnList = "project_id, id")})
@Check(name = "ck_arch_integrations_direction", constraints = "direction IN ('INBOUND', 'OUTBOUND', 'BIDIRECTIONAL')")
@Check(name = "ck_arch_integrations_interaction_type", constraints = "interaction_type IN ('API', 'EVENT', 'FILE_EXCHANGE', 'BATCH', 'OTHER')")
@Check(name = "ck_arch_integrations_auth_category", constraints = "auth_category IN ('NONE', 'SERVICE_ACCOUNT', 'OAUTH2', 'MTLS', 'CERTIFICATE', 'OTHER')")
@Check(name = "ck_arch_integrations_criticality", constraints = "criticality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')")
@Check(name = "ck_arch_integrations_readiness", constraints = "readiness IN ('PLANNED', 'IN_PROGRESS', 'READY', 'BLOCKED')")
@Check(name = "ck_arch_integrations_version", constraints = "version >= 0")
public class ArchitectureIntegrationEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "source_system", nullable = false, length = 255)
    private String sourceSystem;

    @Column(name = "target_system", nullable = false, length = 255)
    private String targetSystem;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "interaction_type", nullable = false, length = 32)
    private String interactionType;

    @Column(name = "auth_category", nullable = false, length = 32)
    private String authCategory;

    @Column(name = "criticality", nullable = false, length = 16)
    private String criticality;

    @Column(name = "readiness", nullable = false, length = 32)
    private String readiness;

    @Column(name = "responsible_party", nullable = true, length = 255)
    private String responsibleParty;

    @Column(name = "comment", nullable = true, length = 1000)
    private String comment;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arch_integration_passport"))
    private ArchitecturePassportEntity passport;

    public String getId() { return id; }
    public void setId(String value) { id = value; }

    public String getName() { return name; }
    public void setName(String value) { name = value; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String value) { sourceSystem = value; }

    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String value) { targetSystem = value; }

    public String getDirection() { return direction; }
    public void setDirection(String value) { direction = value; }

    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String value) { interactionType = value; }

    public String getAuthCategory() { return authCategory; }
    public void setAuthCategory(String value) { authCategory = value; }

    public String getCriticality() { return criticality; }
    public void setCriticality(String value) { criticality = value; }

    public String getReadiness() { return readiness; }
    public void setReadiness(String value) { readiness = value; }

    public String getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(String value) { responsibleParty = value; }

    public String getComment() { return comment; }
    public void setComment(String value) { comment = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }

    public ArchitecturePassportEntity getPassport() { return passport; }
    public void setPassport(ArchitecturePassportEntity value) { passport = value; }
}
