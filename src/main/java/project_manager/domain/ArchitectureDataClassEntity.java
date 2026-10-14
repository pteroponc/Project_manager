package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_architecture_data_classes")
@Check(name = "ck_arch_data_classes_class_code", constraints = "class_code IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'PERSONAL', 'PAYMENT', 'COMMERCIAL_SECRET')")
public class ArchitectureDataClassEntity {
    @EmbeddedId
    private ArchitectureDataClassId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arch_data_passport"))
    private ArchitecturePassportEntity passport;

    public ArchitectureDataClassId getId() { return id; }
    public void setId(ArchitectureDataClassId value) { id = value; }

    public ArchitecturePassportEntity getPassport() { return passport; }
    public void setPassport(ArchitecturePassportEntity value) { passport = value; }
}
