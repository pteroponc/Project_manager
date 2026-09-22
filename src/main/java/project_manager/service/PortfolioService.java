package project_manager.service;

import project_manager.domain.PortfolioSnapshot;
import project_manager.domain.ProjectEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {
    private final ProjectService projectService;

    public PortfolioService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public PortfolioSnapshot getSnapshot(String quarter, String health) {
        return getSnapshot(quarter, health, "all");
    }

    public PortfolioSnapshot getSnapshot(String quarter, String health, String status) {
        List<ProjectEntity> filtered = projectService.findProjects(quarter, health, status);
        int active = (int) filtered.stream().filter(project -> "active".equalsIgnoreCase(project.getStatus())).count();
        int risky = (int) filtered.stream().filter(project -> !"green".equalsIgnoreCase(project.getHealth())).count();
        int budget = filtered.stream().map(ProjectEntity::getBudget).filter(java.util.Objects::nonNull)
            .mapToInt(Integer::intValue).sum();
        int averageProgress = (int) filtered.stream().map(ProjectEntity::getProgress)
            .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
        return new PortfolioSnapshot(filtered.size(), active, risky, budget, averageProgress);
    }
}
