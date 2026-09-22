package project_manager.web.dto;

public record ProjectCardResponse(
    String calculationDate,
    String timeZone,
    String id,
    String name,
    String owner,
    String status,
    String health,
    String deliveryModel,
    int progress,
    String quarter,
    String deadline,
    String milestone,
    String risk,
    String dependency,
    String kpiName,
    String kpiTarget,
    String summary,
    ProjectAssessmentResponse assessment,
    BoardSummary board
) {
    public record BoardSummary(boolean viewAvailable, long taskCount) { }
}
