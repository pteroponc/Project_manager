package project_manager.service;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final ProjectRepository projectRepository;
    private final ProjectWriteLock projectWriteLock;
    private final BoardCardRepository boardCardRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ArchitectureReadService architectureReads;

    public ProjectDeletionService(ProjectRepository projectRepository,
                                  ProjectWriteLock projectWriteLock,
                                  BoardCardRepository boardCardRepository,
                                  ProjectMilestoneRepository milestoneRepository, ArchitectureReadService architectureReads) {
        this.projectRepository = projectRepository;
        this.projectWriteLock = projectWriteLock;
        this.boardCardRepository = boardCardRepository;
        this.milestoneRepository = milestoneRepository;
        this.architectureReads = architectureReads;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectDeletionImpactResponse getImpact(String id) {
        var architecture = architectureReads.projectDependencies(id);
        var project = architecture.project();
        var counts = architecture.counts();
        long taskCount = boardCardRepository.countByProjectId(id);
        long milestoneCount = milestoneRepository.countByProject_Id(id);
        List<String> blockers = new ArrayList<>(blockers(taskCount, milestoneCount));
        if (counts.architecturePassportCount() > 0) blockers.add("У проекта есть архитектурный паспорт");
        if (counts.componentCount() > 0) blockers.add("Компоненты: " + counts.componentCount());
        if (counts.integrationCount() > 0) blockers.add("Интеграции: " + counts.integrationCount());
        if (counts.checkCount() > 0) blockers.add("Проверки: " + counts.checkCount());
        if (counts.securityRiskCount() > 0) blockers.add("Риски ИБ: " + counts.securityRiskCount());
        return new ProjectDeletionImpactResponse(
            id, project.name(), taskCount, milestoneCount, blockers.isEmpty(), List.copyOf(blockers),
            counts.architecturePassportCount(), counts.componentCount(), counts.integrationCount(),
            counts.checkCount(), counts.securityRiskCount(), true);
    }

    @Transactional
    public void delete(String id) {
        ProjectEntity project = projectWriteLock.require(id);
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
        try {
            projectRepository.delete(project);
            projectRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ProjectConflictException("PROJECT_HAS_DEPENDENCIES",
                "Project has dependent records and cannot be deleted");
        }
    }

    private List<String> blockers(long taskCount, long milestoneCount) {
        List<String> blockers = new ArrayList<>();
        if (taskCount > 0) blockers.add("У проекта есть задачи: " + taskCount);
        if (milestoneCount > 0) blockers.add("У проекта есть контрольные точки: " + milestoneCount);
        return List.copyOf(blockers);
    }
}
