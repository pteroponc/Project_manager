package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_integration_data_classes")
@Check(name = "ck_integration_data_classes_class_code", constraints = "class_code IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'PERSONAL', 'PAYMENT', 'COMMERCIAL_SECRET')")
public class IntegrationDataClassEntity {
    @EmbeddedId
    private IntegrationDataClassId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("integrationId")
    @JoinColumn(name = "integration_id", nullable = false, foreignKey = @ForeignKey(name = "fk_integration_data_integration"))
    private ArchitectureIntegrationEntity integration;

    public IntegrationDataClassId getId() { return id; }
    public void setId(IntegrationDataClassId value) { id = value; }

    public ArchitectureIntegrationEntity getIntegration() { return integration; }
    public void setIntegration(ArchitectureIntegrationEntity value) { integration = value; }
}
