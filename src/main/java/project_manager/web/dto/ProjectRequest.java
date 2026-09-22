package project_manager.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class ProjectRequest {
    @NotBlank private String name;
    @NotBlank private String owner;
    @NotBlank private String status;
    @NotBlank private String health;
    @NotBlank private String deliveryModel;
    @Min(0) private Integer budget;
    @Min(0) @Max(100) private Integer progress;
    private LocalDate startDate;
    @NotBlank private String quarter;
    @NotBlank private String deadline;
    @NotBlank private String milestone;
    @NotBlank private String risk;
    @NotBlank private String dependency;
    @NotBlank private String kpiName;
    @NotBlank private String kpiTarget;
    @NotBlank private String summary;

    private boolean budgetPresent;
    private boolean progressPresent;
    private boolean startDatePresent;

    public String name() { return name; }
    public String owner() { return owner; }
    public String status() { return status; }
    public String health() { return health; }
    public String deliveryModel() { return deliveryModel; }
    public Integer budget() { return budget; }
    public Integer progress() { return progress; }
    public LocalDate startDate() { return startDate; }
    public String quarter() { return quarter; }
    public String deadline() { return deadline; }
    public String milestone() { return milestone; }
    public String risk() { return risk; }
    public String dependency() { return dependency; }
    public String kpiName() { return kpiName; }
    public String kpiTarget() { return kpiTarget; }
    public String summary() { return summary; }

    public boolean budgetPresent() { return budgetPresent; }
    public boolean progressPresent() { return progressPresent; }
    public boolean startDatePresent() { return startDatePresent; }

    public void setName(String name) { this.name = name; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setStatus(String status) { this.status = status; }
    public void setHealth(String health) { this.health = health; }
    public void setDeliveryModel(String deliveryModel) { this.deliveryModel = deliveryModel; }
    public void setBudget(Integer budget) { this.budget = budget; this.budgetPresent = true; }
    public void setProgress(Integer progress) { this.progress = progress; this.progressPresent = true; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; this.startDatePresent = true; }
    public void setQuarter(String quarter) { this.quarter = quarter; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public void setMilestone(String milestone) { this.milestone = milestone; }
    public void setRisk(String risk) { this.risk = risk; }
    public void setDependency(String dependency) { this.dependency = dependency; }
    public void setKpiName(String kpiName) { this.kpiName = kpiName; }
    public void setKpiTarget(String kpiTarget) { this.kpiTarget = kpiTarget; }
    public void setSummary(String summary) { this.summary = summary; }
}
