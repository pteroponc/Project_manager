package project_manager.web;

import project_manager.service.ProjectCardService;
import project_manager.service.ProjectDeletionService;
import project_manager.service.ProjectRegistryService;
import project_manager.service.ProjectService;
import project_manager.web.dto.ProjectRequest;
import project_manager.web.dto.ProjectResponse;
import project_manager.web.dto.ProjectCardResponse;
import project_manager.web.dto.ProjectDeletionImpactResponse;
import project_manager.web.dto.ProjectRegistryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectRegistryService projectRegistryService;
    private final ProjectCardService projectCardService;
    private final ProjectDeletionService projectDeletionService;

    public ProjectController(ProjectService projectService, ProjectRegistryService projectRegistryService,
                             ProjectCardService projectCardService, ProjectDeletionService projectDeletionService) {
        this.projectService = projectService;
        this.projectRegistryService = projectRegistryService;
        this.projectCardService = projectCardService;
        this.projectDeletionService = projectDeletionService;
    }

    @GetMapping("/projects")
    public List<ProjectResponse> projects(
        @RequestParam(defaultValue = "all") String quarter,
        @RequestParam(defaultValue = "all") String health,
        @RequestParam(defaultValue = "all") String status
    ) {
        return projectService.getProjects(quarter, health, status);
    }

    @GetMapping("/projects/registry")
    public ProjectRegistryResponse registry(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(defaultValue = "all") String quarter,
        @RequestParam(defaultValue = "all") String health,
        @RequestParam(defaultValue = "all") String status,
        @RequestParam(defaultValue = "all") String attention,
        @RequestParam(defaultValue = "name") String sort,
        @RequestParam(defaultValue = "asc") String direction
    ) {
        return projectRegistryService.getRegistry(query, quarter, status, health, attention, sort, direction);
    }

    @GetMapping("/projects/{id}/card")
    public ProjectCardResponse card(@PathVariable String id) {
        return projectCardService.getCard(id);
    }

    @GetMapping("/projects/{id}/deletion-impact")
    public ProjectDeletionImpactResponse deletionImpact(@PathVariable String id) {
        return projectDeletionService.getImpact(id);
    }

    @GetMapping("/projects/{id}")
    public ProjectResponse project(@PathVariable String id) {
        return projectService.getProject(id);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return projectService.createProject(request);
    }

    @PutMapping("/projects/{id}")
    public ProjectResponse update(@PathVariable String id, @Valid @RequestBody ProjectRequest request) {
        return projectService.updateProject(id, request);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        projectDeletionService.delete(id);
    }
}
