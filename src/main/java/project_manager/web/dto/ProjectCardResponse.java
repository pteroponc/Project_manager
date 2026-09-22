package project_manager.web.dto;

import java.time.LocalDate;
import java.util.List;

public record ProjectCardResponse(
    String calculationDate,
    String timeZone,
    String id,
    String name,
    String owner,
    String status,
    String health,
    String deliveryModel,
    Integer progress,
    LocalDate startDate,
    Long version,
    String quarter,
    String deadline,
    String milestone,
    String risk,
    String dependency,
    String kpiName,
    String kpiTarget,
    String summary,
    ProjectAssessmentResponse assessment,
    BoardSummary board,
    long milestoneCount,
    List<MilestoneResponse> milestones
) {
    public record BoardSummary(boolean viewAvailable, long taskCount) { }

    public record MilestoneResponse(
        String id,
        String projectId,
        String name,
        LocalDate plannedDate,
        boolean completed,
        LocalDate completedDate,
        int position
    ) { }
}
