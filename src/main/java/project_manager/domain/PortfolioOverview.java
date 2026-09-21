package project_manager.domain;

import java.util.List;
import java.util.Map;

/** Read-only presentation DTO, not a persistence model. */
public record PortfolioOverview(
    String calculationDate, String timeZone, int totalProjectCount, int projectCount, int activeCount,
    Map<String, Integer> statusCounts, Map<String, Integer> healthCounts,
    Long totalBudget, int budgetCoverage, Integer averageProgress, int progressCoverage,
    int attentionCount, int overdueCount, int dueSoonCount,
    List<String> quarters, List<String> statuses, List<String> healthValues,
    List<ProjectOverviewItem> projects, List<ProjectOverviewItem> attentionProjects,
    List<ProjectOverviewItem> milestones, List<ProjectOverviewItem> dataQualityProjects
) {
    public record ProjectOverviewItem(
        String id, String name, String owner, String status, String health,
        int budget, int progress, String quarter, String deadline, String milestone,
        String risk, List<String> attentionReasons, List<String> dataQualityIssues, Long daysUntilDeadline
    ) { }
}
