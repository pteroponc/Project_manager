package project_manager.domain;

import java.time.LocalDate;
import java.util.List;

public record ArchitectureReadinessAssessment(
    Status status, String statusLabel, List<Reason> reasons, List<UpcomingCheck> upcomingChecks,
    List<AcceptedRisk> acceptedHighRisks, LocalDate calculationDate, String timeZone,
    boolean officialGoLiveApproval
) {
    public ArchitectureReadinessAssessment {
        reasons = List.copyOf(reasons);
        upcomingChecks = List.copyOf(upcomingChecks);
        acceptedHighRisks = List.copyOf(acceptedHighRisks);
    }

    /** Declaration order is the agreed precedence. */
    public enum Status {
        BLOCKED("Заблокировано"), ATTENTION_REQUIRED("Требует внимания"),
        NOT_ASSESSED("Оценка не завершена"), READY("Архитектура и ИБ: проверки завершены");
        private final String label;
        Status(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
    public enum EntityType { PASSPORT, CHECK, INTEGRATION, RISK }
    public enum AcceptanceVerification { UNVERIFIED }

    public enum ReasonCode {
        PASSPORT_MISSING, REQUIRED_INFORMATION_INCOMPLETE, ARCHITECTURE_NOT_APPROVED,
        ARCHITECTURE_CHANGES_REQUIRED, CHECKS_INCOMPLETE, CHECK_TYPE_MISSING, CHECK_TYPE_DUPLICATE,
        CHECK_DATA_INVALID, CHECK_INCOMPLETE, CHECK_BLOCKED, PRODUCTION_ADMISSION_CHANGES_REQUIRED,
        CHECK_CHANGES_REQUIRED, CHECK_OVERDUE, CHECK_PLANNED_DATE_MISSING,
        RISK_CRITICAL_OPEN, RISK_HIGH_OPEN, ACCEPTED_HIGH_RISK, RISK_DATA_INVALID,
        RISK_ACCEPTANCE_JUSTIFICATION_MISSING, INTEGRATION_BLOCKED, INTEGRATION_NOT_READY,
        INTEGRATION_DATA_INVALID
    }

    public record Reason(Status status, ReasonCode code, EntityType entityType, String entityId, String message) { }
    public record UpcomingCheck(String id, String checkType, String status, LocalDate plannedDate) { }
    public record AcceptedRisk(
        String id, RiskLevel level, String justification, AcceptanceVerification acceptanceVerification
    ) { }
}
