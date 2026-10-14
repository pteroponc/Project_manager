package project_manager.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.Instant;

/** Storage only: no delete cascades, lifecycle writes or readiness calculation. */
@Entity
@Table(name = "project_architecture_checks",
    indexes = {@Index(name = "idx_arch_check_date", columnList = "project_id, planned_date, id"), @Index(name = "idx_arch_check_status", columnList = "project_id, status")},
    uniqueConstraints = {@UniqueConstraint(name = "uq_arch_check_type", columnNames = {"project_id", "check_type"})})
@Check(name = "ck_arch_checks_check_type", constraints = "check_type IN ('ARCHITECTURE_APPROVAL', 'SECURITY_REQUIREMENTS', 'DATA_CLASSIFICATION', 'THREAT_MODEL', 'SECURITY_ASSESSMENT', 'ACCESS_CONTROL', 'LOGGING_MONITORING', 'BACKUP_RECOVERY', 'LOAD_TESTING', 'PRODUCTION_ADMISSION')")
@Check(name = "ck_arch_checks_status", constraints = "status IN ('NOT_REQUIRED', 'NOT_STARTED', 'IN_PROGRESS', 'IN_REVIEW', 'PASSED', 'CHANGES_REQUIRED', 'BLOCKED')")
@Check(name = "ck_arch_checks_version", constraints = "version >= 0")
public class ArchitectureCheckEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "check_type", nullable = false, length = 48)
    private String checkType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "owner", nullable = true, length = 255)
    private String owner;

    @Column(name = "planned_date", nullable = true)
    private LocalDate plannedDate;

    @Column(name = "actual_date", nullable = true)
    private LocalDate actualDate;

    @Column(name = "comment", nullable = true, length = 2000)
    private String comment;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arch_check_passport"))
    private ArchitecturePassportEntity passport;

    public String getId() { return id; }
    public void setId(String value) { id = value; }

    public String getCheckType() { return checkType; }
    public void setCheckType(String value) { checkType = value; }

    public boolean getRequired() { return required; }
    public void setRequired(boolean value) { required = value; }

    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }

    public String getOwner() { return owner; }
    public void setOwner(String value) { owner = value; }

    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate value) { plannedDate = value; }

    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate value) { actualDate = value; }

    public String getComment() { return comment; }
    public void setComment(String value) { comment = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }

    public ArchitecturePassportEntity getPassport() { return passport; }
    public void setPassport(ArchitecturePassportEntity value) { passport = value; }
}
