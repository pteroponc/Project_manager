package project_manager.domain;

public record PortfolioSnapshot(
    int projectCount,
    int activeCount,
    int riskyCount,
    int totalBudget,
    int averageProgress
) {
}
