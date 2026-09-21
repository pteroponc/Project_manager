package project_manager.service;

import project_manager.domain.PortfolioOverview;
import project_manager.domain.PortfolioOverview.ProjectOverviewItem;
import project_manager.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PortfolioOverviewService {
    static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Set<String> STATUSES = Set.of("active", "planned", "paused", "done");
    private static final Set<String> HEALTH = Set.of("green", "yellow", "red");
    private final ProjectService projectService;
    private final ProjectRepository repository;
    private final Clock clock;

    @Autowired
    public PortfolioOverviewService(ProjectService projectService, ProjectRepository repository) {
        this(projectService, repository, Clock.system(ZONE));
    }

    public PortfolioOverviewService(ProjectService projectService, ProjectRepository repository, Clock clock) {
        this.projectService = projectService;
        this.repository = repository;
        this.clock = clock.withZone(ZONE);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PortfolioOverview getOverview(String quarter, String health, String status) {
        var projects = projectService.findProjects(quarter, health, status);
        LocalDate today = LocalDate.now(clock);
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> healthCounts = new LinkedHashMap<>();
        List<ProjectOverviewItem> rows = new ArrayList<>();
        List<ProjectOverviewItem> attention = new ArrayList<>();
        List<ProjectOverviewItem> milestones = new ArrayList<>();
        List<ProjectOverviewItem> quality = new ArrayList<>();
        long totalBudget = 0, progressTotal = 0;
        int budgetCoverage = 0, progressCoverage = 0, overdueCount = 0;

        for (var project : projects) {
            String projectStatus = normalized(project.getStatus());
            String projectHealth = normalized(project.getHealth());
            boolean done = "done".equals(projectStatus);
            statusCounts.merge(STATUSES.contains(projectStatus) ? projectStatus : "unknown", 1, Integer::sum);
            healthCounts.merge(HEALTH.contains(projectHealth) ? projectHealth : "unknown", 1, Integer::sum);
            List<String> issues = new ArrayList<>();
            if (!STATUSES.contains(projectStatus)) issues.add("Неизвестный статус: " + project.getStatus());
            if (!HEALTH.contains(projectHealth)) issues.add("Неизвестное состояние: " + project.getHealth());
            if (project.getBudget() >= 0) {
                totalBudget += project.getBudget();
                budgetCoverage++;
            } else issues.add("Некорректный указанный бюджет");
            if (project.getProgress() >= 0 && project.getProgress() <= 100) {
                progressTotal += project.getProgress();
                progressCoverage++;
            } else issues.add("Некорректный указанный прогресс");

            LocalDate deadline = parseDate(project.getDeadline());
            Long days = deadline == null ? null : deadline.toEpochDay() - today.toEpochDay();
            List<String> reasons = new ArrayList<>();
            if ("red".equals(projectHealth)) {
                if (done) issues.add("Противоречие данных: проект завершён, но состояние красное");
                else reasons.add("Красное состояние");
            }
            if (days == null) issues.add("Не удалось определить срок проекта: " + project.getDeadline());
            else if (!done && days < 0) {
                overdueCount++;
                reasons.add("Срок проекта просрочен на " + (-days) + " дн.");
            }
            if (project.getMilestone() == null || project.getMilestone().isBlank()) {
                issues.add("Контрольная точка не указана");
            }
            var item = new ProjectOverviewItem(project.getId(), project.getName(), project.getOwner(),
                project.getStatus(), project.getHealth(), project.getBudget(), project.getProgress(), project.getQuarter(),
                project.getDeadline(), project.getMilestone(), project.getRisk(), List.copyOf(reasons), List.copyOf(issues), days);
            rows.add(item);
            if (!reasons.isEmpty()) attention.add(item);
            if (!issues.isEmpty()) quality.add(item);
            // Planned dates of completed projects remain visible, but never count as overdue.
            if (days != null && days >= 0 && days <= 14) milestones.add(item);
        }
        attention.sort(Comparator.comparingInt((ProjectOverviewItem item) -> item.attentionReasons().size()).reversed()
            .thenComparing(ProjectOverviewItem::daysUntilDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ProjectOverviewItem::name).thenComparing(ProjectOverviewItem::id));
        milestones.sort(Comparator.comparing(ProjectOverviewItem::daysUntilDeadline)
            .thenComparing(ProjectOverviewItem::name).thenComparing(ProjectOverviewItem::id));
        return new PortfolioOverview(today.toString(), ZONE.getId(), Math.toIntExact(repository.count()), projects.size(),
            statusCounts.getOrDefault("active", 0), statusCounts, healthCounts,
            budgetCoverage == 0 ? null : totalBudget, budgetCoverage,
            progressCoverage == 0 ? null : (int) Math.round((double) progressTotal / progressCoverage), progressCoverage,
            attention.size(), overdueCount, milestones.size(), repository.findQuarters(), repository.findStatuses(),
            repository.findHealthValues(), rows, attention, milestones, quality);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
