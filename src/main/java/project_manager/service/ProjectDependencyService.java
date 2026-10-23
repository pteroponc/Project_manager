package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import project_manager.repository.ArchitectureReadRepository;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.web.dto.ProjectDeletionImpactResponse;
import java.util.ArrayList;
import java.util.List;

/** Shared scalar counts/rules. Caller supplies either a read snapshot or the project write lock. */
@Service
public class ProjectDependencyService {
    private final BoardCardRepository cards;
    private final ProjectMilestoneRepository milestones;
    private final ArchitectureReadRepository architecture;
    public ProjectDependencyService(BoardCardRepository cards, ProjectMilestoneRepository milestones, ArchitectureReadRepository architecture) {
        this.cards=cards; this.milestones=milestones; this.architecture=architecture;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public ProjectDeletionImpactResponse impact(String id, String name) {
        long tasks=cards.countByProjectId(id), points=milestones.countByProject_Id(id);
        var counts=architecture.projectDeletionCounts(id);
        List<String> blockers=new ArrayList<>();
        if (tasks>0) blockers.add("У проекта есть задачи: "+tasks);
        if (points>0) blockers.add("У проекта есть контрольные точки: "+points);
        if (counts.architecturePassportCount()>0) blockers.add("У проекта есть архитектурный паспорт");
        if (counts.componentCount()>0) blockers.add("Компоненты: "+counts.componentCount());
        if (counts.integrationCount()>0) blockers.add("Интеграции: "+counts.integrationCount());
        if (counts.checkCount()>0) blockers.add("Проверки: "+counts.checkCount());
        if (counts.securityRiskCount()>0) blockers.add("Риски ИБ: "+counts.securityRiskCount());
        return new ProjectDeletionImpactResponse(id,name,tasks,points,blockers.isEmpty(),List.copyOf(blockers),
            counts.architecturePassportCount(),counts.componentCount(),counts.integrationCount(),counts.checkCount(),counts.securityRiskCount(),false);
    }
    public static String conflictCode(ProjectDeletionImpactResponse impact) {
        boolean architecture=impact.architecturePassportCount()>0 || impact.componentCount()>0 || impact.integrationCount()>0
            || impact.checkCount()>0 || impact.securityRiskCount()>0;
        if (architecture) return "PROJECT_HAS_DEPENDENCIES";
        if (impact.taskCount()>0 && impact.milestoneCount()==0) return "PROJECT_HAS_BOARD_CARDS";
        if (impact.milestoneCount()>0 && impact.taskCount()==0) return "PROJECT_HAS_MILESTONES";
        return "PROJECT_HAS_DEPENDENCIES";
    }
}
