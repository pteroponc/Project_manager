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
        List<ProjectEntity> filtered = projectService.findProjects(quarter, health);
        int active = (int) filtered.stream().filter(project -> "active".equalsIgnoreCase(project.getStatus())).count();
        int risky = (int) filtered.stream().filter(project -> !"green".equalsIgnoreCase(project.getHealth())).count();
        int budget = filtered.stream().mapToInt(ProjectEntity::getBudget).sum();
        int averageProgress = filtered.isEmpty() ? 0 : (int) filtered.stream().mapToInt(ProjectEntity::getProgress).average().orElse(0);
        return new PortfolioSnapshot(filtered.size(), active, risky, budget, averageProgress);
    }
}
