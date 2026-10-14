package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_architecture_passports",
    indexes = {@Index(name = "idx_arch_passport_portfolio", columnList = "criticality, stage, project_id")})
@Check(name = "ck_arch_passports_criticality", constraints = "criticality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')")
@Check(name = "ck_arch_passports_stage", constraints = "stage IN ('NOT_STARTED', 'IN_DESIGN', 'IN_REVIEW', 'APPROVED', 'CHANGES_REQUIRED')")
@Check(name = "ck_arch_passports_solution_type", constraints = "solution_type IN ('NEW_SOLUTION', 'EXISTING_SOLUTION_CHANGE', 'INTEGRATION', 'MIGRATION')")
@Check(name = "ck_arch_passports_version", constraints = "version >= 0")
public class ArchitecturePassportEntity {

    @Id
    @Column(name = "project_id", nullable = false, length = 255)
    private String projectId;

    @Column(name = "criticality", nullable = false, length = 16)
    private String criticality;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "solution_type", nullable = false, length = 32)
    private String solutionType;

    @Column(name = "architect", nullable = true, length = 255)
    private String architect;

    @Column(name = "solution_summary", nullable = true, length = 2000)
    private String solutionSummary;

    @Column(name = "availability_requirement", nullable = true, length = 1000)
    private String availabilityRequirement;

    @Column(name = "recovery_requirement", nullable = true, length = 1000)
    private String recoveryRequirement;

    @Column(name = "last_review_date", nullable = true)
    private LocalDate lastReviewDate;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arch_passport_project"))
    private ProjectEntity project;

    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { projectId = value; }

    public String getCriticality() { return criticality; }
    public void setCriticality(String value) { criticality = value; }

    public String getStage() { return stage; }
    public void setStage(String value) { stage = value; }

    public String getSolutionType() { return solutionType; }
    public void setSolutionType(String value) { solutionType = value; }

    public String getArchitect() { return architect; }
    public void setArchitect(String value) { architect = value; }

    public String getSolutionSummary() { return solutionSummary; }
    public void setSolutionSummary(String value) { solutionSummary = value; }

    public String getAvailabilityRequirement() { return availabilityRequirement; }
    public void setAvailabilityRequirement(String value) { availabilityRequirement = value; }

    public String getRecoveryRequirement() { return recoveryRequirement; }
    public void setRecoveryRequirement(String value) { recoveryRequirement = value; }

    public LocalDate getLastReviewDate() { return lastReviewDate; }
    public void setLastReviewDate(LocalDate value) { lastReviewDate = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }

    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity value) { project = value; }
}
