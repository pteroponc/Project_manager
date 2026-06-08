package project_manager.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProjectRequest(
    @NotBlank String name,
    @NotBlank String owner,
    @NotBlank String status,
    @NotBlank String health,
    @NotBlank String deliveryModel,
    @NotNull @Min(0) Integer budget,
    @NotNull @Min(0) @Max(100) Integer progress,
    @NotBlank String quarter,
    @NotBlank String deadline,
    @NotBlank String milestone,
    @NotBlank String risk,
    @NotBlank String dependency,
    @NotBlank String kpiName,
    @NotBlank String kpiTarget,
    @NotBlank String summary
) {
}
