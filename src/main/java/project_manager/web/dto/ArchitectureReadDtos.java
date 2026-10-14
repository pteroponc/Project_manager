package project_manager.web.dto;

import project_manager.domain.ArchitectureReadinessAssessment;
import project_manager.domain.ArchitectureReadinessAssessment.RiskLevel;
import project_manager.domain.ArchitectureReadinessAssessment.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.EnumMap;

/** Scalar read projections and API responses. No JPA objects cross the service boundary. */
public final class ArchitectureReadDtos {
    private ArchitectureReadDtos() { }

    public record ProjectInfo(String id, String name, String owner, String quarter, String projectStatus, Long version) { }
    public record Passport(String projectId, String criticality, String stage, String solutionType, String architect,
        String solutionSummary, String availabilityRequirement, String recoveryRequirement, LocalDate lastReviewDate,
        Long version, Instant createdAt, Instant updatedAt) { }
    public record Membership(String ownerId, String code) { }
    public record Component(String id, String projectId, String name, String componentType, String purpose,
        String platform, String criticality, boolean externalComponent, int position, Long version) { }
    public record IntegrationData(String id, String projectId, String name, String sourceSystem, String targetSystem,
        String direction, String interactionType, String authCategory, String criticality, String readiness,
        String responsibleParty, String comment, Long version) { }
    public record Integration(String id, String projectId, String name, String sourceSystem, String targetSystem,
        String direction, String interactionType, String authCategory, String criticality, String readiness,
        String responsibleParty, String comment, Long version, List<String> dataClasses) {
        public Integration { dataClasses = List.copyOf(dataClasses); }
    }
    public record Check(String id, String projectId, String checkType, boolean required, String status, String owner,
        LocalDate plannedDate, LocalDate actualDate, String comment, Long version) { }
    public record RiskData(String id, String projectId, String name, String description, String category,
        String probability, String impact, String owner, LocalDate treatmentDueDate, String treatmentPlan,
        String status, LocalDate closedDate, String acceptanceJustification, Long version) { }
    public record Risk(String id, String projectId, String name, String description, String category,
        String probability, String impact, String owner, LocalDate treatmentDueDate, String treatmentPlan,
        String status, LocalDate closedDate, String acceptanceJustification, Long version, RiskLevel level,
        String acceptanceVerification) { }
    public record Card(ProjectInfo project, Passport passport, List<String> dataClasses, List<String> environments,
        List<Component> components, List<Integration> integrations, List<Check> checks, List<Risk> risks,
        ArchitectureReadinessAssessment assessment) {
        public Card {
            dataClasses = List.copyOf(dataClasses); environments = List.copyOf(environments);
            components = List.copyOf(components); integrations = List.copyOf(integrations);
            checks = List.copyOf(checks); risks = List.copyOf(risks);
        }
    }
    public record PortfolioItem(ProjectInfo project, boolean passportPresent, String criticality,
        ArchitectureReadinessAssessment assessment) { }
    public record Summary(Map<Status, Long> readinessCounts, long acceptedHighRiskCount,
        long passportPresentCount, long passportMissingCount) {
        public Summary { readinessCounts = Collections.unmodifiableMap(new EnumMap<>(readinessCounts)); }
    }
    public record Portfolio(LocalDate calculationDate, String timeZone, long totalCount, int page, int size,
        long totalPages, Summary summary, List<PortfolioItem> items) {
        public Portfolio { items = List.copyOf(items); }
    }
    public record Counts(long architecturePassportCount, long componentCount, long integrationCount,
        long checkCount, long securityRiskCount, long dataClassCount, long environmentCount) { }
    public record ProjectDependencies(ProjectInfo project, Counts counts) { }
    public record PassportDeletionImpact(String projectId, boolean passportPresent, long componentCount,
        long integrationCount, long checkCount, long securityRiskCount, long dataClassCount, long environmentCount,
        boolean deletionAllowed, List<String> blockers) {
        public PassportDeletionImpact { blockers = List.copyOf(blockers); }
    }
}
