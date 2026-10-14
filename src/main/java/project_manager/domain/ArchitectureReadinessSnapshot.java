package project_manager.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable calculation input. It has no JPA entities, project health or delivery dates. */
public record ArchitectureReadinessSnapshot(
    String projectId, Passport passport, Set<String> dataClasses, Set<String> environments,
    List<Check> checks, List<Integration> integrations, List<Risk> risks
) {
    public ArchitectureReadinessSnapshot {
        Objects.requireNonNull(projectId, "Project identifier is required");
        if (projectId.isBlank()) throw new IllegalArgumentException("Project identifier is required");
        dataClasses = dataClasses == null ? Set.of() : Set.copyOf(dataClasses);
        environments = environments == null ? Set.of() : Set.copyOf(environments);
        checks = checks == null ? List.of() : List.copyOf(checks);
        integrations = integrations == null ? List.of() : List.copyOf(integrations);
        risks = risks == null ? List.of() : List.copyOf(risks);
    }

    public record Passport(
        String criticality, String stage, String solutionType, String architect, String solutionSummary,
        String availabilityRequirement, String recoveryRequirement, LocalDate lastReviewDate
    ) { }

    public record Check(
        String id, String checkType, Boolean required, String status,
        LocalDate plannedDate, LocalDate actualDate, String comment
    ) { }

    public record Integration(String id, String criticality, String readiness) { }

    public record Risk(String id, String probability, String impact, String status, String acceptanceJustification) { }
}
