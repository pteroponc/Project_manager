package project_manager.web;

import org.springframework.web.bind.annotation.*;
import project_manager.service.ProjectDashboardService;
import project_manager.web.dto.ProjectDashboardResponse;

@RestController
@RequestMapping("/api/projects")
public class ProjectDashboardController {
    private final ProjectDashboardService service;
    public ProjectDashboardController(ProjectDashboardService service) { this.service = service; }

    @GetMapping("/{id}/dashboard")
    public ProjectDashboardResponse dashboard(@PathVariable String id) { return service.getDashboard(id); }
}
