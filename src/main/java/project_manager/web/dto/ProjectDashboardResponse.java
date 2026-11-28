package project_manager.web.dto;

import java.time.Instant;
import java.util.List;
import project_manager.web.dto.ProjectCardResponse.MilestoneResponse;

public record ProjectDashboardResponse(String calculationDate, String timeZone, Instant generatedAt,
    String model, ProjectCardResponse project, Tasks tasks, Milestones milestones, Dash03 dash03) {
    public record ColumnCount(String key, String title, long count) { }
    public record Task(String id, String title, String columnKey, String owner, String dueDate,
                       Boolean blocked, Long version) { }
    public record Tasks(long total, long done, long blocked, long overdue, long uncertain,
        long unknownDates, long unmapped, long unknownBlocked, List<ColumnCount> columns,
        List<ColumnCount> unassigned, List<Task> blockedTasks, List<Task> overdueTasks) { }
    public record Milestones(List<MilestoneResponse> overdue, List<MilestoneResponse> dueSoon,
                             long unknownDates) { }
    public record Dash03(long backlog, long ready, long inProgress, long done, long open,
        long highPriority, long overdue, long unknownDates, long unknownPriorities,
        List<DashTask> overdueTasks, List<DashTask> upcomingTasks,
        List<DashTask> criticalUndatedTasks) { }
    public record DashTask(String id, String title, String columnKey, String dueDate,
        String priority, String owner) { }
}
