package project_manager.web.dto;

import java.time.LocalDate;

public record ProjectResponse(
    String id,
    String name,
    String owner,
    String status,
    String health,
    String deliveryModel,
    Integer budget,
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
    String summary
) {
}
