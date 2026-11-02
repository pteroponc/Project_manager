package project_manager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record BoardCardRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 10000) String description,
    @NotBlank @Size(max = 255) String owner,
    @NotBlank @Size(max = 255) String dueDate,
    @NotBlank @Size(max = 255) String columnKey,
    @NotBlank @Size(max = 255) String priority,
    @Size(max = 255) String labels,
    @Min(0) int estimate,
    boolean blocked,
    Long expectedVersion
) {
    public BoardCardRequest(String title, String description, String owner, String dueDate,
                            String columnKey, String priority, String labels, int estimate, boolean blocked) {
        this(title, description, owner, dueDate, columnKey, priority, labels, estimate, blocked, null);
    }

    public BoardCardRequest withExpectedVersion(long version) {
        return new BoardCardRequest(title, description, owner, dueDate, columnKey, priority,
            labels, estimate, blocked, version);
    }
}
