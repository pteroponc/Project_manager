package project_manager.web.dto;

import jakarta.validation.constraints.NotBlank;

public record BoardCardRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotBlank String owner,
    @NotBlank String dueDate,
    @NotBlank String columnKey
) {
}
