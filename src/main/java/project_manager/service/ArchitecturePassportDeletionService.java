package project_manager.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.repository.ArchitecturePassportRepository;
import project_manager.repository.ArchitectureReadRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.ArchitectureMutationException;
import project_manager.web.ArchitecturePassportDependenciesException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.util.Map;

@Service
public class ArchitecturePassportDeletionService {
    private final ProjectWriteLock projectLock;
    private final ProjectRepository projects;
    private final ArchitecturePassportRepository passports;
    private final ArchitectureReadRepository repository;
    private final ArchitectureReadService reads;
    private final EntityManager em;
    public ArchitecturePassportDeletionService(ProjectWriteLock projectLock, ProjectRepository projects,
        ArchitecturePassportRepository passports, ArchitectureReadRepository repository, ArchitectureReadService reads, EntityManager em) {
        this.projectLock = projectLock; this.projects = projects; this.passports = passports;
        this.repository = repository; this.reads = reads; this.em = em;
    }

    @Transactional
    public Card delete(String id, Map<String, String[]> parameters) {
        var input = ArchitecturePassportDeletionInput.parse(parameters);
        var project = projectLock.require(id);
        var passport = passports.findForUpdate(id).orElseThrow(ArchitectureMutationException::missingPassport);
        ArchitecturePassportInstance.check(passport, input.expectedPassportInstanceId());
        if (passport.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("PASSPORT", passport.getVersion());
        var impact = ArchitectureReadService.passportImpact(id, repository.dependenciesOfExistingPassport(id));
        if (!impact.deletionAllowed()) throw new ArchitecturePassportDependenciesException(impact, passport.getVersion());
        // No child is deleted or cleared. NO ACTION foreign keys remain the final constraint.
        em.remove(passport); em.flush();
        if (projects.incrementVersionIfCurrent(id, project.getVersion()) != 1) {
            Long current = projects.currentVersion(id);
            if (current == null) throw ProjectMutationException.projectNotFound();
            throw ArchitectureMutationException.version("PROJECT", current);
        }
        em.refresh(project); em.flush();
        return reads.cardAfterWrite(id);
    }
}
