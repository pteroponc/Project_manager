package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_architecture_components",
    indexes = {@Index(name = "idx_arch_component_order", columnList = "project_id, position, id")})
@Check(name = "ck_arch_components_component_type", constraints = "component_type IN ('FRONTEND', 'BACKEND', 'DATABASE', 'QUEUE', 'EXTERNAL_SERVICE', 'INFRASTRUCTURE')")
@Check(name = "ck_arch_components_criticality", constraints = "criticality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')")
@Check(name = "ck_arch_components_position", constraints = "position >= 0")
@Check(name = "ck_arch_components_version", constraints = "version >= 0")
public class ArchitectureComponentEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "component_type", nullable = false, length = 32)
    private String componentType;

    @Column(name = "purpose", nullable = true, length = 1000)
    private String purpose;

    @Column(name = "platform", nullable = true, length = 255)
    private String platform;

    @Column(name = "criticality", nullable = false, length = 16)
    private String criticality;

    @Column(name = "external_component", nullable = false)
    private boolean externalComponent;

    @Column(name = "position", nullable = false)
    private int position;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arch_component_passport"))
    private ArchitecturePassportEntity passport;

    public String getId() { return id; }
    public void setId(String value) { id = value; }

    public String getName() { return name; }
    public void setName(String value) { name = value; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String value) { componentType = value; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { purpose = value; }

    public String getPlatform() { return platform; }
    public void setPlatform(String value) { platform = value; }

    public String getCriticality() { return criticality; }
    public void setCriticality(String value) { criticality = value; }

    public boolean getExternalComponent() { return externalComponent; }
    public void setExternalComponent(boolean value) { externalComponent = value; }

    public int getPosition() { return position; }
    public void setPosition(int value) { position = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }

    public ArchitecturePassportEntity getPassport() { return passport; }
    public void setPassport(ArchitecturePassportEntity value) { passport = value; }
}
