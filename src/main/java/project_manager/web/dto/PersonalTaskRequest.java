package project_manager.web.dto;

public record PersonalTaskRequest(String projectId, String title, String description, String dueDate,
                                  String priority, String columnKey, Long expectedVersion) { }
