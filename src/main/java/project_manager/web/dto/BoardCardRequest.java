package project_manager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

public record BoardCardRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotBlank String owner,
    @NotBlank String dueDate,
    @NotBlank String columnKey,
    @NotBlank String priority,
    String labels,
    @Min(0) int estimate,
    boolean blocked
) {
}
