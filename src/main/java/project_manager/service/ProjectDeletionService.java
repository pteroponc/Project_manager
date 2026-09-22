package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ProjectEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.ProjectConflictException;
import project_manager.web.dto.ProjectDeletionImpactResponse;

import java.util.List;
import java.util.ArrayList;

@Service
public class ProjectDeletionService {
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final BoardCardRepository boardCardRepository;
    private final ProjectMilestoneRepository milestoneRepository;

    public ProjectDeletionService(ProjectService projectService, ProjectRepository projectRepository,
                                  BoardCardRepository boardCardRepository,
                                  ProjectMilestoneRepository milestoneRepository) {
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.boardCardRepository = boardCardRepository;
        this.milestoneRepository = milestoneRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectDeletionImpactResponse getImpact(String id) {
        ProjectEntity project = projectService.requireProject(id);
        long taskCount = boardCardRepository.countByProjectId(id);
        long milestoneCount = milestoneRepository.countByProject_Id(id);
        List<String> blockers = blockers(taskCount, milestoneCount);
        return new ProjectDeletionImpactResponse(
            id, project.getName(), taskCount, milestoneCount, blockers.isEmpty(), blockers);
    }

    @Transactional
    public void delete(String id) {
        ProjectEntity project = projectService.requireProject(id);
        long taskCount = boardCardRepository.countByProjectId(id);
        long milestoneCount = milestoneRepository.countByProject_Id(id);
        if (taskCount > 0 || milestoneCount > 0) {
            String code = taskCount > 0 && milestoneCount == 0
                ? "PROJECT_HAS_BOARD_CARDS"
                : milestoneCount > 0 && taskCount == 0
                    ? "PROJECT_HAS_MILESTONES"
                    : "PROJECT_HAS_DEPENDENCIES";
            throw new ProjectConflictException(code, "Project has dependent records and cannot be deleted: " + id);
        }
        projectRepository.delete(project);
    }

    private List<String> blockers(long taskCount, long milestoneCount) {
        List<String> blockers = new ArrayList<>();
        if (taskCount > 0) blockers.add("У проекта есть задачи: " + taskCount);
        if (milestoneCount > 0) blockers.add("У проекта есть контрольные точки: " + milestoneCount);
        return List.copyOf(blockers);
    }
}
