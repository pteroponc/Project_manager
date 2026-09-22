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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioOverviewService {
    private final ProjectService projectService;
    private final ProjectRepository repository;
    private final ProjectAssessmentService assessmentService;

    @Autowired
    public PortfolioOverviewService(ProjectService projectService, ProjectRepository repository,
                                    ProjectAssessmentService assessmentService) {
        this.projectService = projectService;
        this.repository = repository;
        this.assessmentService = assessmentService;
    }

    public PortfolioOverviewService(ProjectService projectService, ProjectRepository repository, Clock clock) {
        this(projectService, repository, new ProjectAssessmentService(clock));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PortfolioOverview getOverview(String quarter, String health, String status) {
        var projects = projectService.findProjects(quarter, health, status);
        LocalDate today = assessmentService.calculationDate();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> healthCounts = new LinkedHashMap<>();
        List<ProjectOverviewItem> rows = new ArrayList<>();
        List<ProjectOverviewItem> attention = new ArrayList<>();
        List<ProjectOverviewItem> milestones = new ArrayList<>();
        List<ProjectOverviewItem> quality = new ArrayList<>();
        long totalBudget = 0, progressTotal = 0;
        int budgetCoverage = 0, progressCoverage = 0, overdueCount = 0;

        for (var project : projects) {
            var assessment = assessmentService.assess(project, today);
            String projectStatus = assessment.normalizedStatus();
            String projectHealth = assessment.normalizedHealth();
            statusCounts.merge(assessmentService.isKnownStatus(projectStatus) ? projectStatus : "unknown", 1,
                Integer::sum);
            healthCounts.merge(assessmentService.isKnownHealth(projectHealth) ? projectHealth : "unknown", 1,
                Integer::sum);
            if (project.getBudget() != null && project.getBudget() >= 0) {
                totalBudget += project.getBudget();
                budgetCoverage++;
            }
            if (project.getProgress() != null && project.getProgress() >= 0 && project.getProgress() <= 100) {
                progressTotal += project.getProgress();
                progressCoverage++;
            }

            if (assessment.overdue()) {
                overdueCount++;
            }
            var item = new ProjectOverviewItem(project.getId(), project.getName(), project.getOwner(),
                project.getStatus(), project.getHealth(), project.getBudget(), project.getProgress(), project.getQuarter(),
                project.getDeadline(), project.getMilestone(), project.getRisk(), assessment.attentionReasons(),
                assessment.dataQualityIssues(), assessment.daysUntilDeadline());
            rows.add(item);
            if (assessment.requiresAttention()) attention.add(item);
            if (!assessment.dataQualityIssues().isEmpty()) quality.add(item);
            // Planned dates of completed projects remain visible, but never count as overdue.
            Long days = assessment.daysUntilDeadline();
            if (days != null && days >= 0 && days <= 14) milestones.add(item);
        }
        attention.sort(Comparator.comparingInt((ProjectOverviewItem item) -> item.attentionReasons().size()).reversed()
            .thenComparing(ProjectOverviewItem::daysUntilDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ProjectOverviewItem::name).thenComparing(ProjectOverviewItem::id));
        milestones.sort(Comparator.comparing(ProjectOverviewItem::daysUntilDeadline)
            .thenComparing(ProjectOverviewItem::name).thenComparing(ProjectOverviewItem::id));
        return new PortfolioOverview(today.toString(), ProjectAssessmentService.ZONE.getId(),
            Math.toIntExact(repository.count()), projects.size(),
            statusCounts.getOrDefault("active", 0), statusCounts, healthCounts,
            budgetCoverage == 0 ? null : totalBudget, budgetCoverage,
            progressCoverage == 0 ? null : (int) Math.round((double) progressTotal / progressCoverage), progressCoverage,
            attention.size(), overdueCount, milestones.size(), repository.findQuarters(), repository.findStatuses(),
            repository.findHealthValues(), rows, attention, milestones, quality);
    }

}
