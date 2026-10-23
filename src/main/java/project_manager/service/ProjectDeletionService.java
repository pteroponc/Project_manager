package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import project_manager.repository.ProjectRepository;
import project_manager.web.ProjectConflictException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ProjectDeletionImpactResponse;

@Service
public class ProjectDeletionService {
    private final ProjectRepository projects;
    private final ProjectWriteLock projectLock;
    private final ProjectDependencyService dependencies;
    public ProjectDeletionService(ProjectRepository projects, ProjectWriteLock projectLock, ProjectDependencyService dependencies) {
        this.projects=projects; this.projectLock=projectLock; this.dependencies=dependencies;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ProjectDeletionImpactResponse getImpact(String id) {
        var project=projects.findById(id).orElseThrow(ProjectMutationException::projectNotFound);
        return dependencies.impact(id,project.getName());
    }

    @Transactional
    public void delete(String id) {
        var project=projectLock.require(id);
        // All runtime dependency creation/transfer paths hold this same lock until commit.
        var impact=dependencies.impact(id,project.getName());
        if (!impact.deletionAllowed()) throw new ProjectConflictException(ProjectDependencyService.conflictCode(impact),
            "Project has dependent records and cannot be deleted: "+id,impact);
        projects.delete(project);
        projects.flush();
    }
}
