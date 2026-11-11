package project_manager.web.dto;

import java.time.Instant;
import java.util.List;
import project_manager.web.dto.ProjectCardResponse.MilestoneResponse;

public record ProjectDashboardResponse(String calculationDate, String timeZone, Instant generatedAt,
    String model, ProjectCardResponse project, Tasks tasks, Milestones milestones) {
    public record ColumnCount(String key, String title, long count) { }
    public record Task(String id, String title, String columnKey, String owner, String dueDate,
                       Boolean blocked, Long version) { }
    public record Tasks(long total, long done, long blocked, long overdue, long uncertain,
        long unknownDates, long unmapped, long unknownBlocked, List<ColumnCount> columns,
        List<ColumnCount> unassigned, List<Task> blockedTasks, List<Task> overdueTasks) { }
    public record Milestones(List<MilestoneResponse> overdue, List<MilestoneResponse> dueSoon,
                             long unknownDates) { }
}
