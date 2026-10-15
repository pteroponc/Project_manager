package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.SecurityRiskEntity;
import project_manager.repository.ArchitecturePassportRepository;
import project_manager.repository.ProjectRepository;
import project_manager.repository.SecurityRiskRepository;
import project_manager.web.ArchitectureMutationException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.util.UUID;

@Service
public class ArchitectureRiskMutationService {
    private final ProjectWriteLock projectLock;
    private final ProjectRepository projects;
    private final ArchitecturePassportRepository passports;
    private final SecurityRiskRepository risks;
    private final ArchitectureReadService reads;
    private final EntityManager em;

    public ArchitectureRiskMutationService(ProjectWriteLock projectLock, ProjectRepository projects,
        ArchitecturePassportRepository passports, SecurityRiskRepository risks, ArchitectureReadService reads, EntityManager em) {
        this.projectLock = projectLock; this.projects = projects; this.passports = passports;
        this.risks = risks; this.reads = reads; this.em = em;
    }

    @Transactional
    public Card create(String projectId, JsonNode body) {
        var input = ArchitectureRiskInput.parse(body, true);
        var risk = new SecurityRiskEntity();
        input.merge(risk).applyTo(risk);
        projectLock.require(projectId);
        var passport = passports.findForUpdate(projectId).orElseThrow(ArchitectureMutationException::missingPassport);
        ArchitecturePassportInstance.check(passport, input.expectedPassportInstanceId());
        if (passport.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("PASSPORT", passport.getVersion());
        risk.setId(UUID.randomUUID().toString()); risk.setPassport(passport);
        // Hibernate assigns the initial @Version; level and acceptance verification are read projections only.
        em.persist(risk); em.flush();
        if (passports.incrementVersionIfCurrent(projectId, input.expectedVersion()) != 1) {
            Long current = passports.currentVersion(projectId);
            if (current == null) throw ArchitectureMutationException.missingPassport();
            throw ArchitectureMutationException.version("PASSPORT", current);
        }
        em.refresh(passport); em.flush();
        return reads.cardAfterWrite(projectId);
    }

    @Transactional
    public Card patch(String projectId, String id, JsonNode body) {
        var input = ArchitectureRiskInput.parse(body, false);
        if (!projects.existsById(projectId)) throw ProjectMutationException.projectNotFound();
        if (!passports.existsById(projectId)) throw ArchitectureMutationException.missingPassport();
        var risk = risks.findForUpdate(id, projectId).orElseThrow(ArchitectureMutationException::missingRisk);
        if (risk.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("RISK", risk.getVersion());
        input.merge(risk).applyTo(risk);
        em.flush();
        if (risk.getVersion() == input.expectedVersion()) {
            if (risks.incrementVersionIfCurrent(id, input.expectedVersion()) != 1) {
                Long current = risks.currentVersion(id, projectId);
                if (current == null) throw ArchitectureMutationException.missingRisk();
                throw ArchitectureMutationException.version("RISK", current);
            }
            em.refresh(risk);
        }
        return reads.cardAfterWrite(projectId);
    }
}
