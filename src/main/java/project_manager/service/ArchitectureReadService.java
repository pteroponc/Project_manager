package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import project_manager.domain.ArchitectureAssessmentContext;
import project_manager.domain.ArchitectureReadinessAssessment.RiskLevel;
import project_manager.domain.ArchitectureReadinessAssessment.Status;
import project_manager.domain.ArchitectureReadinessSnapshot;
import project_manager.repository.ArchitectureReadRepository;
import project_manager.repository.ArchitectureReadRepository.Batch;
import project_manager.web.dto.ArchitecturePortfolioQuery;
import project_manager.web.dto.ArchitectureReadDtos.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArchitectureReadService {
    private final ArchitectureReadRepository repository;
    private final ArchitectureReadinessAssessmentService assessments;

    public ArchitectureReadService(ArchitectureReadRepository repository, ArchitectureReadinessAssessmentService assessments) {
        this.repository = repository; this.assessments = assessments;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Card card(String id) {
        return assembleCard(id);
    }

    /** Joins the mutation transaction; no separate GET/transaction after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Card cardAfterWrite(String id) {
        return assembleCard(id);
    }

    private Card assembleCard(String id) {
        ProjectInfo project = repository.project(id);
        var context = assessments.newContext();
        return card(project, new Grouped(repository.load(List.of(id))), context);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Portfolio portfolio(Map<String, String> parameters) {
        ArchitecturePortfolioQuery query = ArchitecturePortfolioQuery.parse(parameters);
        if (!"all".equals(query.quarter()) && repository.quarters().stream().noneMatch(query.quarter()::equals)) {
            throw ArchitecturePortfolioQuery.invalid();
        }
        var context = assessments.newContext();
        List<ProjectInfo> projects = repository.projects(query);
        var grouped = new Grouped(repository.load(projects.stream().map(ProjectInfo::id).toList(), false));
        List<PortfolioItem> filtered = projects.stream().map(project -> {
            Card card = card(project, grouped, context);
            return new PortfolioItem(project, card.passport() != null,
                card.passport() == null ? null : card.passport().criticality(), card.assessment());
        }).filter(item -> "all".equals(query.readiness()) || item.assessment().status().name().equalsIgnoreCase(query.readiness()))
          .filter(item -> "all".equals(query.criticality())
              || "unspecified".equals(query.criticality()) && criticalityRank(item.criticality()) == null
              || item.criticality() != null && item.criticality().equalsIgnoreCase(query.criticality()))
          .sorted(comparator(query)).toList();
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status status : Status.values()) counts.put(status, 0L);
        long accepted = 0, present = 0;
        for (PortfolioItem item : filtered) {
            counts.merge(item.assessment().status(), 1L, Long::sum);
            accepted += item.assessment().acceptedHighRisks().size();
            if (item.passportPresent()) present++;
        }
        long from = (long) query.page() * query.size();
        List<PortfolioItem> items = from >= filtered.size() ? List.of()
            : filtered.subList((int) from, (int) Math.min(from + query.size(), filtered.size()));
        return new Portfolio(context.calculationDate(), context.timeZone(), filtered.size(), query.page(), query.size(),
            (filtered.size() + (long) query.size() - 1) / query.size(),
            new Summary(counts, accepted, present, filtered.size() - present), items);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectDependencies projectDependencies(String id) {
        ProjectInfo project = repository.project(id);
        Grouped grouped = new Grouped(repository.load(List.of(id)));
        return new ProjectDependencies(project, new Counts(grouped.passports.containsKey(id) ? 1 : 0,
            grouped.components.getOrDefault(id, List.of()).size(), grouped.integrations.getOrDefault(id, List.of()).size(),
            grouped.checks.getOrDefault(id, List.of()).size(), grouped.risks.getOrDefault(id, List.of()).size(),
            grouped.data.getOrDefault(id, List.of()).size(), grouped.environments.getOrDefault(id, List.of()).size()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PassportDeletionImpact passportImpact(String id) {
        Counts counts = projectDependencies(id).counts();
        return passportImpact(id, counts);
    }

    /** Shared pure formatter for GET and the counts rechecked under deletion locks. */
    static PassportDeletionImpact passportImpact(String id, Counts counts) {
        List<String> blockers = new ArrayList<>();
        if (counts.architecturePassportCount() == 0) blockers.add("Паспорт отсутствует");
        blocker(blockers, counts.componentCount(), "Компоненты");
        blocker(blockers, counts.integrationCount(), "Интеграции");
        blocker(blockers, counts.checkCount(), "Проверки");
        blocker(blockers, counts.securityRiskCount(), "Риски ИБ");
        blocker(blockers, counts.dataClassCount(), "Классы данных паспорта");
        blocker(blockers, counts.environmentCount(), "Среды");
        return new PassportDeletionImpact(id, counts.architecturePassportCount() > 0, counts.componentCount(),
            counts.integrationCount(), counts.checkCount(), counts.securityRiskCount(), counts.dataClassCount(),
            counts.environmentCount(), blockers.isEmpty(), blockers);
    }

    private Card card(ProjectInfo project, Grouped grouped, ArchitectureAssessmentContext context) {
        String id = project.id(); Passport passport = grouped.passports.get(id);
        List<String> data = grouped.data.getOrDefault(id, List.of());
        List<String> environments = grouped.environments.getOrDefault(id, List.of());
        List<Check> checks = grouped.checks.getOrDefault(id, List.of());
        List<IntegrationData> rawIntegrations = grouped.integrations.getOrDefault(id, List.of());
        List<RiskData> rawRisks = grouped.risks.getOrDefault(id, List.of());
        var snapshot = new ArchitectureReadinessSnapshot(id, passport == null ? null : new ArchitectureReadinessSnapshot.Passport(
            passport.criticality(), passport.stage(), passport.solutionType(), passport.architect(), passport.solutionSummary(),
            passport.availabilityRequirement(), passport.recoveryRequirement(), passport.lastReviewDate()), Set.copyOf(data), Set.copyOf(environments),
            checks.stream().map(c -> new ArchitectureReadinessSnapshot.Check(c.id(), c.checkType(), c.required(), c.status(),
                c.plannedDate(), c.actualDate(), c.comment())).toList(),
            rawIntegrations.stream().map(i -> new ArchitectureReadinessSnapshot.Integration(i.id(), i.criticality(), i.readiness())).toList(),
            rawRisks.stream().map(r -> new ArchitectureReadinessSnapshot.Risk(r.id(), r.probability(), r.impact(), r.status(), r.acceptanceJustification())).toList());
        List<Integration> integrations = rawIntegrations.stream().map(i -> new Integration(i.id(), i.projectId(), i.name(),
            i.sourceSystem(), i.targetSystem(), i.direction(), i.interactionType(), i.authCategory(), i.criticality(),
            i.readiness(), i.responsibleParty(), i.comment(), i.version(), grouped.integrationData.getOrDefault(i.id(), List.of()))).toList();
        List<Risk> risks = rawRisks.stream().map(r -> new Risk(r.id(), r.projectId(), r.name(), r.description(), r.category(),
            r.probability(), r.impact(), r.owner(), r.treatmentDueDate(), r.treatmentPlan(), r.status(), r.closedDate(),
            r.acceptanceJustification(), r.version(), level(r), "ACCEPTED".equals(r.status()) ? "UNVERIFIED" : null)).toList();
        return new Card(project, passport, data, environments, grouped.components.getOrDefault(id, List.of()), integrations,
            checks, risks, assessments.assess(snapshot, context));
    }

    private RiskLevel level(RiskData risk) {
        try { return assessments.riskLevel(risk.probability(), risk.impact()); }
        catch (IllegalArgumentException invalidData) { return null; }
    }
    private static void blocker(List<String> blockers, long count, String label) {
        if (count > 0) blockers.add(label + ": " + count);
    }
    private static Integer criticalityRank(String value) {
        if (value == null) return null;
        return switch (value) { case "LOW" -> 0; case "MEDIUM" -> 1; case "HIGH" -> 2; case "CRITICAL" -> 3; default -> null; };
    }
    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
    private static Comparator<PortfolioItem> comparator(ArchitecturePortfolioQuery query) {
        boolean descending = "desc".equals(query.direction());
        Comparator<PortfolioItem> key = switch (query.sort()) {
            case "owner" -> key(item -> normalized(item.project().owner()), descending);
            case "quarter" -> key(item -> normalized(item.project().quarter()), descending);
            case "projectstatus" -> key(item -> normalized(item.project().projectStatus()), descending);
            case "readiness" -> key(item -> item.assessment().status().ordinal(), descending);
            case "criticality" -> key(item -> criticalityRank(item.criticality()), descending);
            default -> key(item -> normalized(item.project().name()), descending);
        };
        return key.thenComparing(item -> item.project().id());
    }
    private static <T extends Comparable<? super T>> Comparator<PortfolioItem> key(Function<PortfolioItem, T> value, boolean descending) {
        Comparator<T> values = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(value, Comparator.nullsLast(values));
    }

    private static final class Grouped {
        final Map<String, Passport> passports;
        final Map<String, List<String>> data, environments, integrationData;
        final Map<String, List<Component>> components;
        final Map<String, List<IntegrationData>> integrations;
        final Map<String, List<Check>> checks;
        final Map<String, List<RiskData>> risks;
        Grouped(Batch batch) {
            passports = batch.passports().stream().collect(Collectors.toMap(Passport::projectId, Function.identity()));
            data = memberships(batch.dataClasses()); environments = memberships(batch.environments());
            integrationData = memberships(batch.integrationDataClasses());
            components = group(batch.components(), Component::projectId, Comparator.comparingInt(Component::position).thenComparing(Component::id));
            integrations = group(batch.integrations(), IntegrationData::projectId, Comparator.comparing(IntegrationData::id));
            checks = group(batch.checks(), Check::projectId, Comparator.comparingInt((Check c) -> ArchitectureReadinessAssessmentService.CHECK_TYPES.indexOf(c.checkType())).thenComparing(Check::id));
            risks = group(batch.risks(), RiskData::projectId, Comparator.comparing(RiskData::id));
        }
        private static Map<String, List<String>> memberships(List<Membership> rows) {
            return rows.stream().collect(Collectors.groupingBy(Membership::ownerId,
                Collectors.mapping(Membership::code, Collectors.collectingAndThen(Collectors.toList(), codes -> codes.stream().sorted().toList()))));
        }
        private static <T> Map<String, List<T>> group(List<T> rows, Function<T, String> owner, Comparator<T> order) {
            return rows.stream().sorted(order).collect(Collectors.groupingBy(owner));
        }
    }
}
