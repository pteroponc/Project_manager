package project_manager.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.*;
import project_manager.repository.*;
import project_manager.web.ArchitectureMutationException;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.util.Map;
import java.util.Set;

@Service
public class ArchitectureChildDeletionService {
    public enum Kind { COMPONENT, INTEGRATION, CHECK, RISK }
    private final ProjectWriteLock projectLock;
    private final ArchitecturePassportRepository passports;
    private final ArchitectureComponentRepository components;
    private final ArchitectureIntegrationRepository integrations;
    private final ArchitectureCheckRepository checks;
    private final SecurityRiskRepository risks;
    private final ArchitectureIntegrationClassWriter classes;
    private final ArchitectureReadService reads;
    private final EntityManager em;

    public ArchitectureChildDeletionService(ProjectWriteLock projectLock, ArchitecturePassportRepository passports,
        ArchitectureComponentRepository components, ArchitectureIntegrationRepository integrations,
        ArchitectureCheckRepository checks, SecurityRiskRepository risks, ArchitectureIntegrationClassWriter classes,
        ArchitectureReadService reads, EntityManager em) {
        this.projectLock = projectLock; this.passports = passports; this.components = components;
        this.integrations = integrations; this.checks = checks; this.risks = risks;
        this.classes = classes; this.reads = reads; this.em = em;
    }

    @Transactional
    public Card delete(String projectId, String recordId, Kind kind, Map<String, String[]> parameters) {
        var input = ArchitectureChildDeletionInput.parse(parameters);
        projectLock.require(projectId);
        var passport = passports.findForUpdate(projectId).orElseThrow(ArchitectureMutationException::missingPassport);
        // Lock and check ownership before exposing either version. No writes precede both version checks.
        Object record;
        long version;
        switch (kind) {
            case COMPONENT -> {
                var row = components.findForUpdate(recordId, projectId).orElseThrow(ArchitectureMutationException::missingComponent);
                record = row; version = row.getVersion();
            }
            case INTEGRATION -> {
                var row = integrations.findForUpdate(recordId, projectId).orElseThrow(ArchitectureMutationException::missingIntegration);
                record = row; version = row.getVersion();
            }
            case CHECK -> {
                var row = checks.findForUpdate(recordId, projectId).orElseThrow(ArchitectureMutationException::missingCheck);
                record = row; version = row.getVersion();
            }
            case RISK -> {
                var row = risks.findForUpdate(recordId, projectId).orElseThrow(ArchitectureMutationException::missingRisk);
                record = row; version = row.getVersion();
            }
            default -> throw new IllegalStateException("Unsupported deletion kind");
        }
        if (passport.getVersion() != input.expectedPassportVersion()) throw ArchitectureMutationException.version("PASSPORT", passport.getVersion());
        if (version != input.expectedVersion()) throw ArchitectureMutationException.version(kind.name(), version);
        if (record instanceof ArchitectureIntegrationEntity integration) {
            classes.replace(integration, Set.of());
            em.flush();
        }
        em.remove(record); em.flush();
        if (passports.incrementVersionIfCurrent(projectId, input.expectedPassportVersion()) != 1) {
            Long current = passports.currentVersion(projectId);
            if (current == null) throw ArchitectureMutationException.missingPassport();
            throw ArchitectureMutationException.version("PASSPORT", current);
        }
        em.refresh(passport); em.flush();
        return reads.cardAfterWrite(projectId);
    }
}
