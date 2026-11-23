package project_manager.web.dto;

import java.util.List;

public record PersonalBoardResponse(List<Column> columns, List<Task> legacyTasks) {
    public record Column(String key, String title, List<Task> tasks) { }

    public record Task(String id, String projectId, String projectName, String columnKey,
                       int position, String title, String description, String owner,
                       String dueDate, String priority, String labels, int estimate,
                       boolean blocked, long version) { }
}
