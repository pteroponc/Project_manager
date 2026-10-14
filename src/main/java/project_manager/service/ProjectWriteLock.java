package project_manager.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectRepository;
import project_manager.web.ProjectMutationException;

import java.util.List;

/** All card insert/move and project delete paths acquire the same project row lock. */
@Component
public class ProjectWriteLock {
    private final ProjectRepository projects;

    public ProjectWriteLock(ProjectRepository projects) {
        this.projects = projects;
    }

    public ProjectEntity require(String id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Project write lock requires a transaction");
        }
        return projects.findByIdForUpdate(id).orElseThrow(ProjectMutationException::projectNotFound);
    }

    public void requireBoth(String firstId, String secondId) {
        List<String> ids = List.of(firstId, secondId).stream().distinct().sorted().toList();
        for (String id : ids) require(id);
    }
}
