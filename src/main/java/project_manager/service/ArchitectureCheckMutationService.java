package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ArchitectureCheckEntity;
import project_manager.repository.ArchitectureCheckRepository;
import project_manager.repository.ArchitecturePassportRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.ArchitectureMutationException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.util.UUID;

@Service
public class ArchitectureCheckMutationService {
    private final ProjectWriteLock projectLock;
    private final ProjectRepository projects;
    private final ArchitecturePassportRepository passports;
    private final ArchitectureCheckRepository checks;
    private final ArchitectureReadService reads;
    private final EntityManager em;
    public ArchitectureCheckMutationService(ProjectWriteLock projectLock, ProjectRepository projects,
        ArchitecturePassportRepository passports, ArchitectureCheckRepository checks, ArchitectureReadService reads, EntityManager em) {
        this.projectLock = projectLock; this.projects = projects; this.passports = passports;
        this.checks = checks; this.reads = reads; this.em = em;
    }

    @Transactional
    public Card initialize(String projectId, JsonNode body) {
        long expected = ArchitectureCheckInput.initializationVersion(body);
        projectLock.require(projectId);
        var passport = passports.findForUpdate(projectId).orElseThrow(ArchitectureMutationException::missingPassport);
        if (passport.getVersion() != expected) throw ArchitectureMutationException.version("PASSPORT", passport.getVersion());
        if (checks.anyForProject(projectId)) throw ArchitectureMutationException.checksAlreadyExist();
        for (String type : ArchitectureReadinessAssessmentService.CHECK_TYPES) {
            var check = new ArchitectureCheckEntity();
            check.setId(UUID.randomUUID().toString()); check.setPassport(passport); check.setCheckType(type);
            check.setRequired(true); check.setStatus("NOT_STARTED");
            // Nullable fields remain null; Hibernate assigns the initial @Version on persist.
            em.persist(check);
        }
        em.flush();
        if (passports.incrementVersionIfCurrent(projectId, expected) != 1) {
            Long current = passports.currentVersion(projectId);
            if (current == null) throw ArchitectureMutationException.missingPassport();
            throw ArchitectureMutationException.version("PASSPORT", current);
        }
        em.refresh(passport); em.flush();
        return reads.cardAfterWrite(projectId);
    }

    @Transactional
    public Card patch(String projectId, String id, JsonNode body) {
        var input = ArchitectureCheckInput.patch(body);
        if (!projects.existsById(projectId)) throw ProjectMutationException.projectNotFound();
        if (!passports.existsById(projectId)) throw ArchitectureMutationException.missingPassport();
        var check = checks.findForUpdate(id, projectId).orElseThrow(ArchitectureMutationException::missingCheck);
        if (check.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("CHECK", check.getVersion());
        // No partial changes to the managed entity before the full combination has been validated.
        input.merge(check).applyTo(check);
        em.flush();
        if (check.getVersion() == input.expectedVersion()) {
            if (checks.incrementVersionIfCurrent(id, input.expectedVersion()) != 1) {
                Long current = checks.currentVersion(id, projectId);
                if (current == null) throw ArchitectureMutationException.missingCheck();
                throw ArchitectureMutationException.version("CHECK", current);
            }
            em.refresh(check);
        }
        return reads.cardAfterWrite(projectId);
    }
}
