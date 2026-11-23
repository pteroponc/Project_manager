package project_manager.web.dto;

import java.time.LocalDate;
import java.util.List;

public record MyDayResponse(LocalDate calculationDate, String timeZone, List<Task> tasks,
                            Milestone milestone, boolean hasUnknownStates) {
    public enum Reason { OVERDUE, TODAY, UPCOMING, PRIORITY_WITHOUT_DATE, INVALID_DATE }
    public enum PlannedDateRelation { PAST, TODAY, FUTURE }

    public record Task(String id, String title, String projectId, String projectName, String state,
                       String priority, boolean highPriority, String recordedDueDate, LocalDate dueDate,
                       Reason reason) { }

    public record Milestone(String id, String name, String projectId, String projectName, LocalDate plannedDate,
                            PlannedDateRelation plannedDateRelation) { }
}
