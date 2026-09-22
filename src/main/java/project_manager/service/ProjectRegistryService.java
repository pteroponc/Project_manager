package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectRepository;
import project_manager.web.BadRequestException;
import project_manager.web.dto.ProjectAssessmentResponse;
import project_manager.web.dto.ProjectRegistryResponse;
import project_manager.web.dto.ProjectRegistryResponse.FilterOptions;
import project_manager.web.dto.ProjectRegistryResponse.ProjectRegistryItem;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProjectRegistryService {
    private static final Set<String> ATTENTION_VALUES = Set.of("all", "only");
    private static final Set<String> SORT_VALUES = Set.of("name", "deadline", "health");
    private static final Set<String> DIRECTION_VALUES = Set.of("asc", "desc");
    private final ProjectService projectService;
    private final ProjectRepository repository;
    private final ProjectAssessmentService assessmentService;

    public ProjectRegistryService(ProjectService projectService, ProjectRepository repository,
                                  ProjectAssessmentService assessmentService) {
        this.projectService = projectService;
        this.repository = repository;
        this.assessmentService = assessmentService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectRegistryResponse getRegistry(String query, String quarter, String status, String health,
                                               String attention, String sort, String direction) {
        String attentionValue = allowed(attention, ATTENTION_VALUES, "attention");
        String sortValue = allowed(sort, SORT_VALUES, "sort");
        String directionValue = allowed(direction, DIRECTION_VALUES, "direction");
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        LocalDate calculationDate = assessmentService.calculationDate();

        List<AssessedProject> assessed = projectService.findProjects(quarter, health, status).stream()
            .filter(project -> matches(project, normalizedQuery))
            .map(project -> new AssessedProject(project, assessmentService.assess(project, calculationDate)))
            .filter(item -> !"only".equals(attentionValue) || item.assessment().requiresAttention())
            .sorted(comparator(sortValue, directionValue))
            .toList();

        List<ProjectRegistryItem> items = assessed.stream().map(item -> toItem(item.project(), item.assessment())).toList();
        return new ProjectRegistryResponse(
            calculationDate.toString(),
            ProjectAssessmentService.ZONE.getId(),
            repository.count(),
            items.size(),
            new FilterOptions(repository.findQuarters(), repository.findStatuses(), repository.findHealthValues()),
            items
        );
    }

    private boolean matches(ProjectEntity project, String query) {
        if (query.isEmpty()) return true;
        return contains(project.getName(), query) || contains(project.getSummary(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private Comparator<AssessedProject> comparator(String sort, String direction) {
        boolean descending = "desc".equals(direction);
        Comparator<AssessedProject> key;
        if ("deadline".equals(sort)) {
            Comparator<LocalDate> dates = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
            key = Comparator.comparing(item -> item.assessment().deadline(), Comparator.nullsLast(dates));
        } else if ("health".equals(sort)) {
            key = Comparator.comparingInt((AssessedProject item) ->
                    assessmentService.isKnownHealth(item.assessment().normalizedHealth()) ? 0 : 1)
                .thenComparingInt(item -> (descending ? -1 : 1) * healthRank(item.assessment().normalizedHealth()));
        } else {
            key = Comparator.comparing(item -> normalized(item.project().getName()));
            if (descending) key = key.reversed();
        }
        return key.thenComparing(item -> normalized(item.project().getName()))
            .thenComparing(item -> normalized(item.project().getId()));
    }

    private int healthRank(String health) {
        return switch (health) {
            case "red" -> 0;
            case "yellow" -> 1;
            case "green" -> 2;
            default -> 3;
        };
    }

    private ProjectRegistryItem toItem(ProjectEntity project, ProjectAssessmentService.Assessment assessment) {
        return new ProjectRegistryItem(
            project.getId(), project.getName(), project.getSummary(), project.getStatus(), project.getHealth(),
            project.getQuarter(), project.getProgress(), project.getDeadline(),
            project.getMilestone(), ProjectAssessmentResponse.from(assessment)
        );
    }

    private String allowed(String value, Set<String> allowed, String parameter) {
        String normalized = normalized(value);
        if (!allowed.contains(normalized)) {
            throw new BadRequestException("Unsupported " + parameter + ": " + value);
        }
        return normalized;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record AssessedProject(ProjectEntity project, ProjectAssessmentService.Assessment assessment) { }
}
