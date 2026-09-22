package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ProjectEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.web.dto.ProjectAssessmentResponse;
import project_manager.web.dto.ProjectCardResponse;
import project_manager.web.dto.ProjectCardResponse.BoardSummary;
import project_manager.web.dto.ProjectCardResponse.MilestoneResponse;

import java.time.LocalDate;

@Service
public class ProjectCardService {
    private final ProjectService projectService;
    private final BoardCardRepository boardCardRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectAssessmentService assessmentService;

    public ProjectCardService(ProjectService projectService, BoardCardRepository boardCardRepository,
                              ProjectMilestoneRepository milestoneRepository,
                              ProjectAssessmentService assessmentService) {
        this.projectService = projectService;
        this.boardCardRepository = boardCardRepository;
        this.milestoneRepository = milestoneRepository;
        this.assessmentService = assessmentService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectCardResponse getCard(String id) {
        ProjectEntity project = projectService.requireProject(id);
        LocalDate calculationDate = assessmentService.calculationDate();
        var assessment = assessmentService.assess(project, calculationDate);
        long taskCount = boardCardRepository.countByProjectId(id);
        var milestones = milestoneRepository.findByProject_IdOrderByPositionAscIdAsc(id).stream()
            .map(milestone -> new MilestoneResponse(
                milestone.getId(), milestone.getProjectId(), milestone.getName(), milestone.getPlannedDate(),
                milestone.isCompleted(), milestone.getCompletedDate(), milestone.getPosition()))
            .toList();

        return new ProjectCardResponse(
            calculationDate.toString(), ProjectAssessmentService.ZONE.getId(), project.getId(), project.getName(),
            project.getOwner(), project.getStatus(), project.getHealth(), project.getDeliveryModel(),
            project.getProgress(), project.getStartDate(), project.getVersion(), project.getQuarter(),
            project.getDeadline(),
            project.getMilestone(), project.getRisk(), project.getDependency(), project.getKpiName(),
            project.getKpiTarget(), project.getSummary(), ProjectAssessmentResponse.from(assessment),
            new BoardSummary(true, taskCount), milestones.size(), milestones
        );
    }
}
