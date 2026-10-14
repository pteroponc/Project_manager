package project_manager.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import project_manager.domain.ArchitectureAssessmentContext;
import project_manager.domain.ArchitectureReadinessAssessment;
import project_manager.domain.ArchitectureReadinessSnapshot;
import project_manager.domain.ArchitectureReadinessSnapshot.Check;
import project_manager.domain.ArchitectureReadinessSnapshot.Passport;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static project_manager.domain.ArchitectureReadinessAssessment.*;
import static project_manager.domain.ArchitectureReadinessAssessment.EntityType.*;
import static project_manager.domain.ArchitectureReadinessAssessment.ReasonCode.*;
import static project_manager.domain.ArchitectureReadinessAssessment.Status.*;

/** Pure assessment of an immutable snapshot; never loads or saves domain entities. */
@Service
public class ArchitectureReadinessAssessmentService {
    public static final List<String> CHECK_TYPES = List.of(
        "ARCHITECTURE_APPROVAL", "SECURITY_REQUIREMENTS", "DATA_CLASSIFICATION", "THREAT_MODEL",
        "SECURITY_ASSESSMENT", "ACCESS_CONTROL", "LOGGING_MONITORING", "BACKUP_RECOVERY",
        "LOAD_TESTING", "PRODUCTION_ADMISSION");
    private static final Set<String> LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STAGES = Set.of("NOT_STARTED", "IN_DESIGN", "IN_REVIEW", "APPROVED", "CHANGES_REQUIRED");
    private static final Set<String> SOLUTIONS = Set.of("NEW_SOLUTION", "EXISTING_SOLUTION_CHANGE", "INTEGRATION", "MIGRATION");
    private static final Set<String> DATA_CLASSES = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "PERSONAL", "PAYMENT", "COMMERCIAL_SECRET");
    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "PREPROD", "PROD", "OTHER");
    private static final Set<String> CHECK_STATUSES = Set.of("NOT_REQUIRED", "NOT_STARTED", "IN_PROGRESS", "IN_REVIEW", "PASSED", "CHANGES_REQUIRED", "BLOCKED");
    private static final Set<String> RISK_STATUSES = Set.of("OPEN", "IN_PROGRESS", "ACCEPTED", "MITIGATED", "CLOSED");
    private static final RiskLevel[][] MATRIX = {
        {RiskLevel.LOW, RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH},
        {RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL},
        {RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL, RiskLevel.CRITICAL},
        {RiskLevel.HIGH, RiskLevel.CRITICAL, RiskLevel.CRITICAL, RiskLevel.CRITICAL}
    };
    private static final Comparator<String> TEXT = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<LocalDate> DATE = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Finding> FINDINGS = Comparator
        .comparingInt((Finding f) -> f.reason().status().ordinal())
        .thenComparingInt(f -> f.reason().entityType().ordinal())
        .thenComparingInt(Finding::checkOrder)
        .thenComparingInt(Finding::riskOrder)
        .thenComparing(Finding::date, DATE)
        .thenComparing(f -> f.reason().entityId(), TEXT)
        .thenComparing(f -> f.reason().code().name())
        .thenComparing(f -> f.reason().message());
    private final Clock clock;

    public ArchitectureReadinessAssessmentService(@Qualifier("architectureAssessmentClock") Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock is required").withZone(ArchitectureAssessmentContext.ZONE);
    }

    public ArchitectureAssessmentContext newContext() {
        return new ArchitectureAssessmentContext(LocalDate.now(clock));
    }

    public RiskLevel riskLevel(String probability, String impact) {
        if (!known(LEVELS, probability) || !known(LEVELS, impact)) {
            throw new IllegalArgumentException("Risk probability and impact must be known levels");
        }
        return MATRIX[RiskLevel.valueOf(probability).ordinal()][RiskLevel.valueOf(impact).ordinal()];
    }

    public ArchitectureReadinessAssessment assess(ArchitectureReadinessSnapshot snapshot,
                                                  ArchitectureAssessmentContext context) {
        Objects.requireNonNull(snapshot, "Snapshot is required");
        Objects.requireNonNull(context, "Assessment context is required");
        List<Finding> findings = new ArrayList<>();
        List<UpcomingCheck> upcoming = new ArrayList<>();
        List<AcceptedRisk> accepted = new ArrayList<>();
        assessPassport(snapshot, findings);
        assessChecks(snapshot, context.calculationDate(), findings, upcoming);
        assessIntegrations(snapshot, findings);
        assessRisks(snapshot, findings, accepted);
        List<Reason> reasons = findings.stream().sorted(FINDINGS).map(Finding::reason).toList();
        Status status = reasons.isEmpty() ? READY : reasons.getFirst().status();
        List<UpcomingCheck> sortedUpcoming = upcoming.stream().sorted(Comparator
            .comparing(UpcomingCheck::plannedDate)
            .thenComparingInt(check -> checkOrder(check.checkType()))
            .thenComparing(UpcomingCheck::id, TEXT)
            .thenComparing(UpcomingCheck::status, TEXT)).toList();
        List<AcceptedRisk> sortedAccepted = accepted.stream().sorted(Comparator
            .comparingInt((AcceptedRisk risk) -> -risk.level().ordinal())
            .thenComparing(AcceptedRisk::id, TEXT)
            .thenComparing(AcceptedRisk::justification, TEXT)).toList();
        return new ArchitectureReadinessAssessment(status, status.label(), reasons, sortedUpcoming, sortedAccepted,
            context.calculationDate(), context.timeZone(), false);
    }

    private void assessPassport(ArchitectureReadinessSnapshot snapshot, List<Finding> findings) {
        Passport passport = snapshot.passport();
        String id = snapshot.projectId();
        if (passport == null) {
            add(findings, NOT_ASSESSED, PASSPORT_MISSING, PASSPORT, id, "Паспорт не создан");
            return;
        }
        boolean complete = known(LEVELS, passport.criticality()) && known(STAGES, passport.stage())
            && known(SOLUTIONS, passport.solutionType()) && present(passport.architect())
            && present(passport.solutionSummary()) && present(passport.availabilityRequirement())
            && present(passport.recoveryRequirement()) && passport.lastReviewDate() != null
            && !snapshot.dataClasses().isEmpty() && DATA_CLASSES.containsAll(snapshot.dataClasses())
            && !snapshot.environments().isEmpty() && ENVIRONMENTS.containsAll(snapshot.environments());
        if (!complete) add(findings, NOT_ASSESSED, REQUIRED_INFORMATION_INCOMPLETE, PASSPORT, id, "Сведения паспорта неполны");
        if (!"APPROVED".equals(passport.stage())) {
            add(findings, NOT_ASSESSED, ARCHITECTURE_NOT_APPROVED, PASSPORT, id, "Архитектура не утверждена");
        }
        if ("CHANGES_REQUIRED".equals(passport.stage())) {
            add(findings, ATTENTION_REQUIRED, ARCHITECTURE_CHANGES_REQUIRED, PASSPORT, id, "Архитектура требует изменений");
        }
    }

    private void assessChecks(ArchitectureReadinessSnapshot snapshot, LocalDate today,
                              List<Finding> findings, List<UpcomingCheck> upcoming) {
        Map<String, Long> counts = new HashMap<>();
        for (Check check : snapshot.checks()) {
            if (knownCheckType(check.checkType())) counts.merge(check.checkType(), 1L, Long::sum);
        }
        boolean incomplete = false;
        for (String type : CHECK_TYPES) {
            long count = counts.getOrDefault(type, 0L);
            if (count == 0) {
                incomplete = true;
                checkFinding(findings, NOT_ASSESSED, CHECK_TYPE_MISSING, null, type, null,
                    "Не определена проверка: " + type);
            } else if (count > 1) {
                incomplete = true;
                checkFinding(findings, NOT_ASSESSED, CHECK_TYPE_DUPLICATE, null, type, null,
                    "Тип проверки указан несколько раз: " + type);
            }
        }
        for (Check check : snapshot.checks()) {
            boolean required = Boolean.TRUE.equals(check.required());
            boolean combinationValid = check.required() != null && known(CHECK_STATUSES, check.status())
                && (required ? !"NOT_REQUIRED".equals(check.status())
                    : "NOT_REQUIRED".equals(check.status()) && present(check.comment()))
                && ("PASSED".equals(check.status()) ? check.actualDate() != null : check.actualDate() == null);
            boolean valid = combinationValid && present(check.id()) && knownCheckType(check.checkType());
            boolean completed = valid && (required ? "PASSED".equals(check.status()) : "NOT_REQUIRED".equals(check.status()));
            if (!valid) {
                incomplete = true;
                checkFinding(findings, NOT_ASSESSED, CHECK_DATA_INVALID, check,
                    "Сведения проверки противоречивы или некорректны");
            }
            if (!completed) {
                incomplete = true;
                checkFinding(findings, NOT_ASSESSED, CHECK_INCOMPLETE, check, "Проверка не завершена корректно");
            }
            if (required) {
                if ("BLOCKED".equals(check.status())) {
                    checkFinding(findings, BLOCKED, CHECK_BLOCKED, check, "Обязательная проверка заблокирована");
                } else if ("CHANGES_REQUIRED".equals(check.status())) {
                    if ("PRODUCTION_ADMISSION".equals(check.checkType())) {
                        checkFinding(findings, BLOCKED, PRODUCTION_ADMISSION_CHANGES_REQUIRED, check,
                            "Проверка перед запуском требует изменений");
                    } else {
                        checkFinding(findings, ATTENTION_REQUIRED, CHECK_CHANGES_REQUIRED, check,
                            "Обязательная проверка требует изменений");
                    }
                }
                if (!completed) {
                    if (check.plannedDate() == null) {
                        checkFinding(findings, NOT_ASSESSED, CHECK_PLANNED_DATE_MISSING, check,
                            "Срок незавершённой обязательной проверки не указан");
                    } else if (check.plannedDate().isBefore(today)) {
                        checkFinding(findings, ATTENTION_REQUIRED, CHECK_OVERDUE, check,
                            "Обязательная незавершённая проверка просрочена");
                    } else if (!check.plannedDate().isAfter(today.plusDays(14))) {
                        upcoming.add(new UpcomingCheck(check.id(), check.checkType(), check.status(), check.plannedDate()));
                    }
                }
            }
        }
        if (incomplete) add(findings, NOT_ASSESSED, CHECKS_INCOMPLETE, PASSPORT, snapshot.projectId(), "Проверки не завершены");
    }

    private void assessIntegrations(ArchitectureReadinessSnapshot snapshot, List<Finding> findings) {
        for (var integration : snapshot.integrations()) {
            if (!known(LEVELS, integration.criticality())) {
                add(findings, NOT_ASSESSED, INTEGRATION_DATA_INVALID, INTEGRATION, integration.id(),
                    "Критичность интеграции не определена");
                continue;
            }
            if ("LOW".equals(integration.criticality()) || "MEDIUM".equals(integration.criticality())) continue;
            if (!present(integration.id())) {
                add(findings, NOT_ASSESSED, INTEGRATION_DATA_INVALID, INTEGRATION, integration.id(),
                    "Сведения интеграции некорректны");
            }
            if ("BLOCKED".equals(integration.readiness())) {
                add(findings, BLOCKED, INTEGRATION_BLOCKED, INTEGRATION, integration.id(),
                    "Интеграция высокой критичности заблокирована");
            } else if ("PLANNED".equals(integration.readiness()) || "IN_PROGRESS".equals(integration.readiness())) {
                add(findings, NOT_ASSESSED, INTEGRATION_NOT_READY, INTEGRATION, integration.id(),
                    "Интеграция высокой критичности не готова");
            } else if (!"READY".equals(integration.readiness())) {
                add(findings, NOT_ASSESSED, INTEGRATION_DATA_INVALID, INTEGRATION, integration.id(),
                    "Готовность интеграции не определена");
            }
        }
    }

    private void assessRisks(ArchitectureReadinessSnapshot snapshot, List<Finding> findings, List<AcceptedRisk> accepted) {
        for (var risk : snapshot.risks()) {
            if (!known(LEVELS, risk.probability()) || !known(LEVELS, risk.impact()) || !known(RISK_STATUSES, risk.status())) {
                add(findings, NOT_ASSESSED, RISK_DATA_INVALID, RISK, risk.id(), "Сведения риска некорректны");
                continue;
            }
            RiskLevel level = riskLevel(risk.probability(), risk.impact());
            if (!present(risk.id())) riskFinding(findings, NOT_ASSESSED, RISK_DATA_INVALID, risk.id(), level, "Идентификатор риска не указан");
            boolean high = level == RiskLevel.HIGH || level == RiskLevel.CRITICAL;
            if (high && "ACCEPTED".equals(risk.status())) {
                accepted.add(new AcceptedRisk(risk.id(), level, risk.acceptanceJustification(), AcceptanceVerification.UNVERIFIED));
                riskFinding(findings, ATTENTION_REQUIRED, ACCEPTED_HIGH_RISK, risk.id(), level,
                    "Высокий или критический риск отмечен как принятый; полномочия не проверены");
                if (!present(risk.acceptanceJustification())) {
                    riskFinding(findings, NOT_ASSESSED, RISK_ACCEPTANCE_JUSTIFICATION_MISSING, risk.id(), level,
                        "Обоснование принятого высокого или критического риска не указано");
                }
            } else if (high && ("OPEN".equals(risk.status()) || "IN_PROGRESS".equals(risk.status()))) {
                riskFinding(findings, level == RiskLevel.CRITICAL ? BLOCKED : ATTENTION_REQUIRED,
                    level == RiskLevel.CRITICAL ? RISK_CRITICAL_OPEN : RISK_HIGH_OPEN, risk.id(), level,
                    level == RiskLevel.CRITICAL ? "Критический риск не устранён" : "Высокий риск не устранён");
            }
        }
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean known(Set<String> values, String value) { return value != null && values.contains(value); }
    private static boolean knownCheckType(String type) { return type != null && CHECK_TYPES.contains(type); }
    private static int checkOrder(String type) {
        if (type == null) return CHECK_TYPES.size();
        int index = CHECK_TYPES.indexOf(type);
        return index < 0 ? CHECK_TYPES.size() : index;
    }
    private static void add(List<Finding> findings, Status status, ReasonCode code, EntityType type, String id, String message) {
        findings.add(new Finding(new Reason(status, code, type, id, message), CHECK_TYPES.size(), 0, null));
    }
    private static void checkFinding(List<Finding> findings, Status status, ReasonCode code, Check check, String message) {
        checkFinding(findings, status, code, check.id(), check.checkType(), check.plannedDate(), message);
    }
    private static void checkFinding(List<Finding> findings, Status status, ReasonCode code, String id,
                                     String type, LocalDate date, String message) {
        findings.add(new Finding(new Reason(status, code, CHECK, id, message), checkOrder(type), 0, date));
    }
    private static void riskFinding(List<Finding> findings, Status status, ReasonCode code, String id, RiskLevel level, String message) {
        findings.add(new Finding(new Reason(status, code, RISK, id, message), CHECK_TYPES.size(), -level.ordinal(), null));
    }
    private record Finding(Reason reason, int checkOrder, int riskOrder, LocalDate date) { }
}
