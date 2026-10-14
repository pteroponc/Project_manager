package project_manager.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import project_manager.domain.ArchitectureAssessmentContext;
import project_manager.domain.ArchitectureReadinessAssessment;
import project_manager.domain.ArchitectureReadinessSnapshot;
import project_manager.domain.ArchitectureReadinessSnapshot.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static project_manager.domain.ArchitectureReadinessAssessment.*;
import static project_manager.domain.ArchitectureReadinessAssessment.ReasonCode.*;
import static project_manager.domain.ArchitectureReadinessAssessment.Status.*;

class ArchitectureReadinessAssessmentServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private final ArchitectureReadinessAssessmentService service = new ArchitectureReadinessAssessmentService(
        Clock.fixed(Instant.parse("2026-10-13T21:00:00Z"), ZoneOffset.UTC));
    private final ArchitectureAssessmentContext context = new ArchitectureAssessmentContext(TODAY);

    @ParameterizedTest
    @CsvSource({
        "LOW,LOW,LOW", "LOW,MEDIUM,LOW", "LOW,HIGH,MEDIUM", "LOW,CRITICAL,HIGH",
        "MEDIUM,LOW,LOW", "MEDIUM,MEDIUM,MEDIUM", "MEDIUM,HIGH,HIGH", "MEDIUM,CRITICAL,CRITICAL",
        "HIGH,LOW,MEDIUM", "HIGH,MEDIUM,HIGH", "HIGH,HIGH,CRITICAL", "HIGH,CRITICAL,CRITICAL",
        "CRITICAL,LOW,HIGH", "CRITICAL,MEDIUM,CRITICAL", "CRITICAL,HIGH,CRITICAL", "CRITICAL,CRITICAL,CRITICAL"
    })
    void allSixteenRiskMatrixCells(String probability, String impact, RiskLevel expected) {
        assertThat(service.riskLevel(probability, impact)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "LOW,LOW,OPEN,READY", "LOW,LOW,IN_PROGRESS,READY", "LOW,LOW,ACCEPTED,READY",
        "LOW,LOW,MITIGATED,READY", "LOW,LOW,CLOSED,READY",
        "LOW,HIGH,OPEN,READY", "LOW,HIGH,IN_PROGRESS,READY", "LOW,HIGH,ACCEPTED,READY",
        "LOW,HIGH,MITIGATED,READY", "LOW,HIGH,CLOSED,READY",
        "LOW,CRITICAL,OPEN,ATTENTION_REQUIRED", "LOW,CRITICAL,IN_PROGRESS,ATTENTION_REQUIRED",
        "LOW,CRITICAL,ACCEPTED,ATTENTION_REQUIRED", "LOW,CRITICAL,MITIGATED,READY", "LOW,CRITICAL,CLOSED,READY",
        "MEDIUM,CRITICAL,OPEN,BLOCKED", "MEDIUM,CRITICAL,IN_PROGRESS,BLOCKED",
        "MEDIUM,CRITICAL,ACCEPTED,ATTENTION_REQUIRED", "MEDIUM,CRITICAL,MITIGATED,READY", "MEDIUM,CRITICAL,CLOSED,READY"
    })
    void riskStatusRulesUseComputedLevel(String probability, String impact, String riskStatus, Status expected) {
        var result = assess(withRisks(List.of(new Risk("risk", probability, impact, riskStatus, "Business justification"))));
        assertThat(result.status()).isEqualTo(expected);
        boolean accepted = "ACCEPTED".equals(riskStatus) && expected == ATTENTION_REQUIRED;
        assertThat(result.acceptedHighRisks()).hasSize(accepted ? 1 : 0);
        if (accepted) {
            assertThat(result.reasons()).extracting(Reason::code).containsExactly(ACCEPTED_HIGH_RISK);
            assertThat(result.acceptedHighRisks().getFirst().acceptanceVerification()).isEqualTo(AcceptanceVerification.UNVERIFIED);
            assertThat(result.acceptedHighRisks().getFirst().justification()).isEqualTo("Business justification");
        }
        assertThat(result.officialGoLiveApproval()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "HIGH,BLOCKED,BLOCKED", "CRITICAL,BLOCKED,BLOCKED",
        "HIGH,PLANNED,NOT_ASSESSED", "CRITICAL,PLANNED,NOT_ASSESSED",
        "HIGH,IN_PROGRESS,NOT_ASSESSED", "CRITICAL,IN_PROGRESS,NOT_ASSESSED",
        "HIGH,READY,READY", "CRITICAL,READY,READY",
        "LOW,BLOCKED,READY", "MEDIUM,BLOCKED,READY", "LOW,PLANNED,READY", "MEDIUM,PLANNED,READY",
        "LOW,IN_PROGRESS,READY", "MEDIUM,IN_PROGRESS,READY", "LOW,READY,READY", "MEDIUM,READY,READY"
    })
    void highCriticalIntegrationsAffectStatusAndLowMediumDoNot(String criticality, String readiness, Status expected) {
        var result = assess(withIntegrations(List.of(new Integration("integration", criticality, readiness))));
        assertThat(result.status()).isEqualTo(expected);
        if (expected != READY) assertThat(result.reasons()).hasSize(1).allSatisfy(r ->
            assertThat(r.entityType()).isEqualTo(EntityType.INTEGRATION));
    }

    @Test
    void completeApprovedPassportAndTenCorrectChecksAreReady() {
        var result = assess(baseline());
        assertThat(result.status()).isEqualTo(READY);
        assertThat(result.statusLabel()).isEqualTo("Архитектура и ИБ: проверки завершены");
        assertThat(result.reasons()).isEmpty();
        assertThat(result.upcomingChecks()).isEmpty();
        assertThat(result.calculationDate()).isEqualTo(TODAY);
        assertThat(result.timeZone()).isEqualTo("Europe/Moscow");
        assertThat(result.officialGoLiveApproval()).isFalse();
    }

    @Test
    void allFourAgreedLabelsRemainCalculationOnly() {
        var b = baseline();
        var missing = new ArchitectureReadinessSnapshot("project", null, b.dataClasses(), b.environments(), b.checks(), List.of(), List.of());
        List<ArchitectureReadinessAssessment> results = List.of(
            assess(withRisks(List.of(new Risk("critical", "HIGH", "HIGH", "OPEN", null)))),
            assess(withRisks(List.of(new Risk("high", "LOW", "CRITICAL", "OPEN", null)))),
            assess(missing), assess(b));
        assertThat(results).extracting(ArchitectureReadinessAssessment::statusLabel).containsExactly(
            "Заблокировано", "Требует внимания", "Оценка не завершена", "Архитектура и ИБ: проверки завершены");
        assertThat(results).allSatisfy(result -> assertThat(result.officialGoLiveApproval()).isFalse());
    }

    @Test
    void explicitNotRequiredWithJustificationIsCompleteWithoutActualDateOrDeadline() {
        var result = assess(replaceCheck(new Check("optional", "ARCHITECTURE_APPROVAL", false,
            "NOT_REQUIRED", null, null, "Documented exception")));
        assertThat(result.status()).isEqualTo(READY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"criticality", "stage", "solutionType", "architect", "summary", "availability",
        "recovery", "reviewDate", "dataClasses", "environments", "unknownClass", "unknownEnvironment"})
    void eachRequiredPassportValueAndSetIsNecessary(String missing) {
        Passport p = new Passport(missing.equals("criticality") ? null : "HIGH",
            missing.equals("stage") ? null : "APPROVED", missing.equals("solutionType") ? null : "NEW_SOLUTION",
            missing.equals("architect") ? " \t" : "Architect", missing.equals("summary") ? null : "Summary",
            missing.equals("availability") ? "" : "Availability", missing.equals("recovery") ? null : "Recovery",
            missing.equals("reviewDate") ? null : TODAY.minusDays(1));
        Set<String> data = missing.equals("dataClasses") ? Set.of() : missing.equals("unknownClass") ? Set.of("INVALID") : Set.of("INTERNAL");
        Set<String> env = missing.equals("environments") ? Set.of() : missing.equals("unknownEnvironment") ? Set.of("INVALID") : Set.of("PROD");
        var b = baseline();
        var result = assess(new ArchitectureReadinessSnapshot("project", p, data, env, b.checks(), List.of(), List.of()));
        assertThat(result.status()).isEqualTo(NOT_ASSESSED);
        assertThat(result.reasons()).extracting(Reason::code).contains(REQUIRED_INFORMATION_INCOMPLETE);
        assertThat(result.reasons()).extracting(Reason::code).doesNotContain(PASSPORT_MISSING);
    }

    @Test
    void absentPassportIsDistinctFromIncompleteOrUnapprovedPassport() {
        var b = baseline();
        var result = assess(new ArchitectureReadinessSnapshot("project", null, Set.of(), Set.of(), b.checks(), List.of(), List.of()));
        assertThat(result.status()).isEqualTo(NOT_ASSESSED);
        assertThat(result.reasons()).extracting(Reason::code).containsExactly(PASSPORT_MISSING);
        assertThat(result.reasons().getFirst().entityId()).isEqualTo("project");
    }

    @ParameterizedTest
    @CsvSource({"NOT_STARTED,NOT_ASSESSED", "IN_DESIGN,NOT_ASSESSED", "IN_REVIEW,NOT_ASSESSED",
        "CHANGES_REQUIRED,ATTENTION_REQUIRED", "APPROVED,READY"})
    void architectureStageHasItsOwnReason(String stage, Status expected) {
        var b = baseline();
        var result = assess(new ArchitectureReadinessSnapshot("project", passport(stage), b.dataClasses(), b.environments(),
            b.checks(), List.of(), List.of()));
        assertThat(result.status()).isEqualTo(expected);
        if (expected != READY) assertThat(result.reasons()).extracting(Reason::code).contains(ARCHITECTURE_NOT_APPROVED);
        if (expected == ATTENTION_REQUIRED) assertThat(result.reasons()).extracting(Reason::code).contains(ARCHITECTURE_CHANGES_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARCHITECTURE_APPROVAL", "SECURITY_REQUIREMENTS", "DATA_CLASSIFICATION", "THREAT_MODEL",
        "SECURITY_ASSESSMENT", "ACCESS_CONTROL", "LOGGING_MONITORING", "BACKUP_RECOVERY", "LOAD_TESTING", "PRODUCTION_ADMISSION"})
    void everyOneOfTheTenCheckTypesIsRequired(String missingType) {
        List<Check> checks = baseline().checks().stream().filter(c -> !c.checkType().equals(missingType)).toList();
        var result = assess(withChecks(checks));
        assertThat(result.status()).isEqualTo(NOT_ASSESSED);
        assertThat(result.reasons()).extracting(Reason::code).containsExactly(CHECKS_INCOMPLETE, CHECK_TYPE_MISSING);
        assertThat(result.reasons().getLast().message()).contains(missingType);
        assertThat(result.reasons().getLast().entityId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"NOT_STARTED,NOT_ASSESSED", "IN_PROGRESS,NOT_ASSESSED", "IN_REVIEW,NOT_ASSESSED",
        "BLOCKED,BLOCKED", "CHANGES_REQUIRED,ATTENTION_REQUIRED"})
    void mandatoryUnfinishedCheckNeverGivesReady(String checkStatus, Status expected) {
        var result = assess(replaceCheck(new Check("check", "THREAT_MODEL", true, checkStatus, TODAY.plusDays(7), null, null)));
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.reasons()).extracting(Reason::code).contains(CHECKS_INCOMPLETE, CHECK_INCOMPLETE);
        assertThat(result.reasons()).extracting(Reason::code).doesNotContain(CHECK_OVERDUE, CHECK_DATA_INVALID);
        assertThat(result.upcomingChecks()).extracting(UpcomingCheck::id).containsExactly("check");
    }

    @Test
    void mandatoryProductionAdmissionChangesRequiredBlocksInsteadOfAttention() {
        var result = assess(replaceCheck(new Check("admission", "PRODUCTION_ADMISSION", true,
            "CHANGES_REQUIRED", TODAY.plusDays(7), null, null)));
        assertThat(result.status()).isEqualTo(BLOCKED);
        assertThat(result.reasons()).extracting(Reason::code).contains(PRODUCTION_ADMISSION_CHANGES_REQUIRED)
            .doesNotContain(CHECK_CHANGES_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"passed-without-date", "required-not-required", "optional-passed", "optional-not-started",
        "optional-blocked", "optional-changes", "no-justification", "actual-on-in-progress", "actual-on-not-required",
        "unknown-status", "unknown-type", "null-type", "null-required", "missing-id"})
    void contradictoryChecksFailClosed(String variant) {
        boolean required = !variant.startsWith("optional") && !variant.equals("no-justification") && !variant.equals("actual-on-not-required");
        String status = switch (variant) {
            case "passed-without-date", "optional-passed" -> "PASSED";
            case "required-not-required", "no-justification", "actual-on-not-required" -> "NOT_REQUIRED";
            case "optional-blocked" -> "BLOCKED";
            case "optional-not-started" -> "NOT_STARTED";
            case "optional-changes" -> "CHANGES_REQUIRED";
            case "unknown-status" -> "UNEXPECTED";
            default -> "IN_PROGRESS";
        };
        LocalDate actual = Set.of("optional-passed", "actual-on-in-progress", "actual-on-not-required").contains(variant) ? TODAY : null;
        String type = variant.equals("unknown-type") ? "UNEXPECTED" : variant.equals("null-type") ? null : "THREAT_MODEL";
        var result = assess(replaceCheck(new Check(variant.equals("missing-id") ? null : "check", type,
            variant.equals("null-required") ? null : required, status, TODAY.plusDays(7), actual, " ")));
        assertThat(result.status()).isEqualTo(NOT_ASSESSED);
        assertThat(result.reasons()).extracting(Reason::code).contains(CHECK_DATA_INVALID, CHECKS_INCOMPLETE);
    }

    @Test
    void duplicateTypeCannotMaskAMissingTypeOrBecomeReady() {
        List<Check> checks = new ArrayList<>(baseline().checks());
        checks.add(new Check("duplicate", "THREAT_MODEL", true, "PASSED", TODAY, TODAY, null));
        var result = assess(withChecks(checks));
        assertThat(result.status()).isEqualTo(NOT_ASSESSED);
        assertThat(result.reasons()).extracting(Reason::code).contains(CHECK_TYPE_DUPLICATE, CHECKS_INCOMPLETE);
    }

    @ParameterizedTest
    @CsvSource({"-1,ATTENTION_REQUIRED,false", "0,NOT_ASSESSED,true", "14,NOT_ASSESSED,true", "15,NOT_ASSESSED,false"})
    void deadlineBoundariesUseStrictOverdueAndInclusiveUpcomingWindow(int days, Status expected, boolean upcoming) {
        var result = assess(replaceCheck(new Check("dated", "THREAT_MODEL", true, "IN_PROGRESS", TODAY.plusDays(days), null, null)));
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.upcomingChecks()).hasSize(upcoming ? 1 : 0);
        assertThat(result.reasons()).extracting(Reason::code).contains(CHECK_INCOMPLETE);
        if (days < 0) assertThat(result.reasons()).extracting(Reason::code).contains(CHECK_OVERDUE);
        else assertThat(result.reasons()).extracting(Reason::code).doesNotContain(CHECK_OVERDUE);
    }

    @Test
    void missingPlannedDateIsIncompleteButNeverOverdue() {
        var result = assess(replaceCheck(new Check("undated", "THREAT_MODEL", true, "IN_PROGRESS", null, null, null)));
        assertThat(result.status()).isEqualTo(NOT_ASSESSED);
        assertThat(result.reasons()).extracting(Reason::code).contains(CHECK_PLANNED_DATE_MISSING)
            .doesNotContain(CHECK_OVERDUE);
    }

    @Test
    void passedAndExplicitlyNotRequiredChecksAreNotOverdueOrUpcoming() {
        var passed = assess(replaceCheck(new Check("passed", "THREAT_MODEL", true, "PASSED", TODAY.minusDays(1), TODAY, null)));
        var optional = assess(replaceCheck(new Check("optional", "THREAT_MODEL", false, "NOT_REQUIRED", TODAY.minusDays(1), null, "Exception")));
        assertThat(passed.status()).isEqualTo(READY); assertThat(optional.status()).isEqualTo(READY);
        assertThat(passed.upcomingChecks()).isEmpty(); assertThat(optional.upcomingChecks()).isEmpty();
    }

    @Test
    void invalidPassedCheckWithMissingActualDateStillHasAnOverdueReason() {
        var result = assess(replaceCheck(new Check("bad-passed", "THREAT_MODEL", true, "PASSED", TODAY.minusDays(1), null, null)));
        assertThat(result.status()).isEqualTo(ATTENTION_REQUIRED);
        assertThat(result.reasons()).extracting(Reason::code).contains(CHECK_DATA_INVALID, CHECK_OVERDUE, CHECK_INCOMPLETE);
    }

    @Test
    void acceptedHighRisksAlwaysNeedAttentionAndReturnUnverifiedJustification() {
        var result = assess(withRisks(List.of(new Risk("a", "LOW", "CRITICAL", "ACCEPTED", " "),
            new Risk("b", "CRITICAL", "CRITICAL", "ACCEPTED", "Management note"))));
        assertThat(result.status()).isEqualTo(ATTENTION_REQUIRED);
        assertThat(result.reasons()).extracting(Reason::code).contains(RISK_ACCEPTANCE_JUSTIFICATION_MISSING);
        assertThat(result.acceptedHighRisks()).extracting(AcceptedRisk::id).containsExactly("b", "a");
        assertThat(result.acceptedHighRisks()).allSatisfy(r -> assertThat(r.acceptanceVerification()).isEqualTo(AcceptanceVerification.UNVERIFIED));
    }

    @Test
    void mixedReasonsRetainAllPrioritiesAndIgnoreInputOrder() {
        var b = baseline();
        List<Check> checks = new ArrayList<>(b.checks());
        checks.set(3, new Check("late", "THREAT_MODEL", true, "IN_PROGRESS", TODAY.minusDays(1), null, null));
        List<Risk> risks = List.of(new Risk("critical", "CRITICAL", "HIGH", "OPEN", null),
            new Risk("accepted", "LOW", "CRITICAL", "ACCEPTED", "Decision"));
        List<Integration> integrations = List.of(new Integration("not-ready", "HIGH", "PLANNED"));
        var snapshot = new ArchitectureReadinessSnapshot("project", passport("IN_REVIEW"), b.dataClasses(), b.environments(), checks, integrations, risks);
        var result = assess(snapshot);
        assertThat(result.status()).isEqualTo(BLOCKED);
        assertThat(result.reasons()).extracting(Reason::code).contains(RISK_CRITICAL_OPEN, ACCEPTED_HIGH_RISK,
            CHECK_OVERDUE, CHECKS_INCOMPLETE, CHECK_INCOMPLETE, ARCHITECTURE_NOT_APPROVED, INTEGRATION_NOT_READY);
        assertThat(result.reasons()).extracting(Reason::status).contains(BLOCKED, ATTENTION_REQUIRED, NOT_ASSESSED);
        assertThat(result.reasons()).filteredOn(r -> r.code() == RISK_CRITICAL_OPEN)
            .allSatisfy(r -> {
                assertThat(r.entityType()).isEqualTo(EntityType.RISK);
                assertThat(r.entityId()).isEqualTo("critical");
            });
        for (int seed = 0; seed < 20; seed++) {
            List<Check> shuffledChecks = new ArrayList<>(checks); List<Risk> shuffledRisks = new ArrayList<>(risks);
            Collections.shuffle(shuffledChecks, new Random(seed)); Collections.shuffle(shuffledRisks, new Random(seed));
            assertThat(assess(new ArchitectureReadinessSnapshot("project", snapshot.passport(), b.dataClasses(), b.environments(),
                shuffledChecks, integrations, shuffledRisks))).isEqualTo(result);
        }
    }

    @Test
    void upcomingAndAcceptedListsHaveDeterministicDateTypeLevelAndIdOrder() {
        var b = baseline(); List<Check> checks = new ArrayList<>(b.checks());
        checks.set(0, new Check("a", "ARCHITECTURE_APPROVAL", true, "IN_PROGRESS", TODAY.plusDays(2), null, null));
        checks.set(3, new Check("b", "THREAT_MODEL", true, "IN_PROGRESS", TODAY, null, null));
        List<Risk> risks = List.of(new Risk("z", "LOW", "CRITICAL", "ACCEPTED", "z"),
            new Risk("a", "LOW", "CRITICAL", "ACCEPTED", "a"), new Risk("c", "HIGH", "HIGH", "ACCEPTED", "c"));
        var first = assess(new ArchitectureReadinessSnapshot("project", b.passport(), b.dataClasses(), b.environments(), checks, List.of(), risks));
        Collections.reverse(checks); List<Risk> reversed = new ArrayList<>(risks); Collections.reverse(reversed);
        var second = assess(new ArchitectureReadinessSnapshot("project", b.passport(), b.dataClasses(), b.environments(), checks, List.of(), reversed));
        assertThat(second).isEqualTo(first);
        assertThat(first.upcomingChecks()).extracting(UpcomingCheck::id).containsExactly("b", "a");
        assertThat(first.acceptedHighRisks()).extracting(AcceptedRisk::id).containsExactly("c", "a", "z");
    }

    @Test
    void snapshotAndCallerCollectionsRemainUnchangedAndResultsAreImmutable() {
        var b = baseline(); List<Check> checks = new ArrayList<>(b.checks()); Set<String> data = new HashSet<>(b.dataClasses());
        List<Integration> integrations = new ArrayList<>(List.of(new Integration("i", "HIGH", "PLANNED")));
        List<Risk> risks = new ArrayList<>(List.of(new Risk("r", "HIGH", "HIGH", "ACCEPTED", "Decision")));
        var snapshot = new ArchitectureReadinessSnapshot("project", b.passport(), data, b.environments(), checks, integrations, risks);
        List<Check> beforeChecks = List.copyOf(checks); List<Risk> beforeRisks = List.copyOf(risks);
        var result = assess(snapshot); assertThat(assess(snapshot)).isEqualTo(result);
        assertThat(checks).containsExactlyElementsOf(beforeChecks); assertThat(risks).containsExactlyElementsOf(beforeRisks);
        assertThat(data).isEqualTo(b.dataClasses()); assertThat(integrations).hasSize(1);
        checks.clear(); data.clear(); integrations.clear(); risks.clear();
        assertThat(assess(snapshot)).isEqualTo(result);
        assertThatThrownBy(() -> snapshot.checks().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.reasons().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.acceptedHighRisks().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidCodesFailClosedWithoutReflectingUnsafeInputInMessages() {
        String unsafe = "<script>secret SQL connection-string</script>";
        var result = assess(withRisks(List.of(new Risk("risk", unsafe, "HIGH", "OPEN", null))));
        assertThat(result.status()).isEqualTo(NOT_ASSESSED);
        assertThat(result.reasons()).extracting(Reason::code).containsExactly(RISK_DATA_INVALID);
        assertThat(result.reasons()).allSatisfy(r -> assertThat(r.message()).doesNotContain(unsafe, "SQL", "secret"));
        assertThatThrownBy(() -> service.riskLevel(unsafe, "HIGH")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Risk probability and impact must be known levels");
        assertThat(assess(withIntegrations(List.of(new Integration("i", "HIGH", null)))).status()).isEqualTo(NOT_ASSESSED);
        assertThat(assess(withIntegrations(List.of(new Integration("i", null, "READY")))).status()).isEqualTo(NOT_ASSESSED);
    }

    @Test
    void moscowMidnightAndSharedContextDoNotRereadClockDuringAssessment() {
        TrackingClock clock = new TrackingClock(Instant.parse("2026-10-13T20:59:59Z"));
        var calculation = new ArchitectureReadinessAssessmentService(clock);
        var before = calculation.newContext();
        assertThat(before.calculationDate()).isEqualTo(TODAY.minusDays(1));
        clock.current.set(Instant.parse("2026-10-13T21:00:00Z"));
        assertThat(calculation.assess(baseline(), before).calculationDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(calculation.assess(withRisks(List.of()), before).calculationDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(clock.reads.get()).isEqualTo(1);
        var after = calculation.newContext();
        assertThat(after.calculationDate()).isEqualTo(TODAY);
        assertThat(clock.reads.get()).isEqualTo(2);
        assertThat(after.timeZone()).isEqualTo("Europe/Moscow");
    }

    private ArchitectureReadinessAssessment assess(ArchitectureReadinessSnapshot snapshot) {
        return service.assess(snapshot, context);
    }

    private static Passport passport(String stage) {
        return new Passport("HIGH", stage, "NEW_SOLUTION", "Architect", "Summary", "Availability", "Recovery", TODAY.minusDays(1));
    }

    private static ArchitectureReadinessSnapshot baseline() {
        List<Check> checks = ArchitectureReadinessAssessmentService.CHECK_TYPES.stream()
            .map(type -> new Check("check-" + type, type, true, "PASSED", TODAY.minusDays(1), TODAY, null)).toList();
        return new ArchitectureReadinessSnapshot("project", passport("APPROVED"), Set.of("INTERNAL"), Set.of("PROD"),
            checks, List.of(), List.of());
    }

    private static ArchitectureReadinessSnapshot replaceCheck(Check check) {
        List<Check> checks = new ArrayList<>(baseline().checks());
        int index = check.checkType() == null ? -1 : ArchitectureReadinessAssessmentService.CHECK_TYPES.indexOf(check.checkType());
        checks.set(index < 0 ? 0 : index, check);
        return withChecks(checks);
    }

    private static ArchitectureReadinessSnapshot withChecks(List<Check> checks) {
        var b = baseline();
        return new ArchitectureReadinessSnapshot("project", b.passport(), b.dataClasses(), b.environments(), checks, List.of(), List.of());
    }

    private static ArchitectureReadinessSnapshot withRisks(List<Risk> risks) {
        var b = baseline();
        return new ArchitectureReadinessSnapshot("project", b.passport(), b.dataClasses(), b.environments(), b.checks(), List.of(), risks);
    }

    private static ArchitectureReadinessSnapshot withIntegrations(List<Integration> integrations) {
        var b = baseline();
        return new ArchitectureReadinessSnapshot("project", b.passport(), b.dataClasses(), b.environments(), b.checks(), integrations, List.of());
    }

    private static final class TrackingClock extends Clock {
        private final AtomicReference<Instant> current;
        private final AtomicInteger reads;
        private final ZoneId zone;
        private TrackingClock(Instant instant) { this(new AtomicReference<>(instant), new AtomicInteger(), ZoneOffset.UTC); }
        private TrackingClock(AtomicReference<Instant> current, AtomicInteger reads, ZoneId zone) {
            this.current = current; this.reads = reads; this.zone = zone;
        }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new TrackingClock(current, reads, zone); }
        @Override public Instant instant() { reads.incrementAndGet(); return current.get(); }
    }
}
