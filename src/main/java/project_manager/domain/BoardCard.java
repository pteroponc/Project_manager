package project_manager.domain;

public record BoardCard(
    String id,
    String title,
    String description,
    String owner,
    String dueDate,
    String priority,
    String labels,
    int estimate,
    boolean blocked
) {
}
