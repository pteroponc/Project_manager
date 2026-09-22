package project_manager.web.dto;

import java.util.List;

public record ProjectRegistryResponse(
    String calculationDate,
    String timeZone,
    long totalCount,
    int filteredCount,
    FilterOptions filterOptions,
    List<ProjectRegistryItem> items
) {
    public record FilterOptions(
        List<String> quarters,
        List<String> statuses,
        List<String> healthValues
    ) { }

    public record ProjectRegistryItem(
        String id,
        String name,
        String summary,
        String status,
        String health,
        String quarter,
        Integer progress,
        String deadline,
        String milestone,
        ProjectAssessmentResponse assessment
    ) { }
}
