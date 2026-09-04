package project_manager.web.dto;

public record ReleaseTrainResponse(
    String id,
    String name,
    String status,
    String cadence,
    String quarter,
    String plannedReleaseDate,
    String codeFreezeDate,
    String qaFreezeDate,
    String goLiveDate,
    int capacityPoints,
    int committedPoints,
    int readiness,
    int blockedItems,
    String scope,
    String risk,
    String decision
) {
}
