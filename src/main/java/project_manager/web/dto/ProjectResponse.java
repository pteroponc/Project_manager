package project_manager.web.dto;

public record ProjectResponse(
    String id,
    String name,
    String owner,
    String status,
    String health,
    String deliveryModel,
    int budget,
    int progress,
    String quarter,
    String deadline,
    String milestone,
    String risk,
    String dependency,
    String kpiName,
    String kpiTarget,
    String summary
) {
}
