package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_security_risks",
    indexes = {@Index(name = "idx_security_risk_status", columnList = "project_id, status, id"), @Index(name = "idx_security_risk_date", columnList = "project_id, treatment_due_date, id")})
@Check(name = "ck_security_risks_category", constraints = "category IN ('ACCESS', 'DATA', 'AVAILABILITY', 'INTEGRATION', 'PROCESS', 'OTHER')")
@Check(name = "ck_security_risks_probability", constraints = "probability IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')")
@Check(name = "ck_security_risks_impact", constraints = "impact IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')")
@Check(name = "ck_security_risks_status", constraints = "status IN ('OPEN', 'IN_PROGRESS', 'ACCEPTED', 'MITIGATED', 'CLOSED')")
@Check(name = "ck_security_risks_version", constraints = "version >= 0")
public class SecurityRiskEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", nullable = true, length = 2000)
    private String description;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "probability", nullable = false, length = 16)
    private String probability;

    @Column(name = "impact", nullable = false, length = 16)
    private String impact;

    @Column(name = "owner", nullable = true, length = 255)
    private String owner;

    @Column(name = "treatment_due_date", nullable = true)
    private LocalDate treatmentDueDate;

    @Column(name = "treatment_plan", nullable = true, length = 2000)
    private String treatmentPlan;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "closed_date", nullable = true)
    private LocalDate closedDate;

    @Column(name = "acceptance_justification", nullable = true, length = 2000)
    private String acceptanceJustification;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_risk_passport"))
    private ArchitecturePassportEntity passport;

    public String getId() { return id; }
    public void setId(String value) { id = value; }

    public String getName() { return name; }
    public void setName(String value) { name = value; }

    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }

    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }

    public String getProbability() { return probability; }
    public void setProbability(String value) { probability = value; }

    public String getImpact() { return impact; }
    public void setImpact(String value) { impact = value; }

    public String getOwner() { return owner; }
    public void setOwner(String value) { owner = value; }

    public LocalDate getTreatmentDueDate() { return treatmentDueDate; }
    public void setTreatmentDueDate(LocalDate value) { treatmentDueDate = value; }

    public String getTreatmentPlan() { return treatmentPlan; }
    public void setTreatmentPlan(String value) { treatmentPlan = value; }

    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }

    public LocalDate getClosedDate() { return closedDate; }
    public void setClosedDate(LocalDate value) { closedDate = value; }

    public String getAcceptanceJustification() { return acceptanceJustification; }
    public void setAcceptanceJustification(String value) { acceptanceJustification = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }

    public ArchitecturePassportEntity getPassport() { return passport; }
    public void setPassport(ArchitecturePassportEntity value) { passport = value; }
}
