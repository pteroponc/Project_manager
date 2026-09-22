package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ProjectEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.ProjectConflictException;
import project_manager.web.dto.ProjectDeletionImpactResponse;

import java.util.List;

@Service
public class ProjectDeletionService {
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final BoardCardRepository boardCardRepository;

    public ProjectDeletionService(ProjectService projectService, ProjectRepository projectRepository,
                                  BoardCardRepository boardCardRepository) {
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.boardCardRepository = boardCardRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectDeletionImpactResponse getImpact(String id) {
        ProjectEntity project = projectService.requireProject(id);
        long taskCount = boardCardRepository.countByProjectId(id);
        List<String> blockers = taskCount == 0
            ? List.of()
            : List.of("У проекта есть задачи: " + taskCount);
        return new ProjectDeletionImpactResponse(id, project.getName(), taskCount, blockers.isEmpty(), blockers);
    }

    @Transactional
    public void delete(String id) {
        ProjectEntity project = projectService.requireProject(id);
        long taskCount = boardCardRepository.countByProjectId(id);
        if (taskCount > 0) {
            throw new ProjectConflictException("Project has board cards and cannot be deleted: " + id);
        }
        projectRepository.delete(project);
    }
}
