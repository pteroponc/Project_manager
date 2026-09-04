package project_manager.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReleaseTrainRequest(
    @NotBlank String name,
    @NotBlank String status,
    @NotBlank String cadence,
    @NotBlank String quarter,
    @NotBlank String plannedReleaseDate,
    @NotBlank String codeFreezeDate,
    @NotBlank String qaFreezeDate,
    @NotBlank String goLiveDate,
    @NotNull @Min(0) Integer capacityPoints,
    @NotNull @Min(0) Integer committedPoints,
    @NotNull @Min(0) @Max(100) Integer readiness,
    @NotNull @Min(0) Integer blockedItems,
    @NotBlank String scope,
    @NotBlank String risk,
    @NotBlank String decision
) {
}
