package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.*;
import project_manager.repository.*;
import project_manager.web.ArchitectureMutationException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ArchitectureChildMutationService {
    private final ProjectWriteLock projectLock;
    private final ProjectRepository projects;
    private final ArchitecturePassportRepository passports;
    private final ArchitectureComponentRepository components;
    private final ArchitectureIntegrationRepository integrations;
    private final ArchitectureIntegrationClassWriter classes;
    private final ArchitectureReadService reads;
    private final EntityManager em;

    public ArchitectureChildMutationService(ProjectWriteLock projectLock, ProjectRepository projects,
        ArchitecturePassportRepository passports, ArchitectureComponentRepository components,
        ArchitectureIntegrationRepository integrations, ArchitectureIntegrationClassWriter classes,
        ArchitectureReadService reads, EntityManager em) {
        this.projectLock = projectLock; this.projects = projects; this.passports = passports;
        this.components = components; this.integrations = integrations; this.classes = classes; this.reads = reads; this.em = em;
    }

    @Transactional
    public Card createComponent(String projectId, JsonNode body) {
        var input = ArchitectureChildInput.parse(body, true, false);
        var passport = creationPassport(projectId, input.expectedVersion());
        var component = new ArchitectureComponentEntity();
        component.setId(UUID.randomUUID().toString()); component.setPassport(passport);
        componentFields(component, input.changes());
        em.persist(component); em.flush();
        incrementPassport(passport, input.expectedVersion());
        return reads.cardAfterWrite(projectId);
    }

    @Transactional
    public Card createIntegration(String projectId, JsonNode body) {
        var input = ArchitectureChildInput.parse(body, true, true);
        var passport = creationPassport(projectId, input.expectedVersion());
        var integration = new ArchitectureIntegrationEntity();
        integration.setId(UUID.randomUUID().toString()); integration.setPassport(passport);
        integrationFields(integration, input.changes());
        em.persist(integration); em.flush();
        incrementPassport(passport, input.expectedVersion());
        replaceClasses(integration, input.changes()); em.flush();
        return reads.cardAfterWrite(projectId);
    }

    @Transactional
    public Card patchComponent(String projectId, String id, JsonNode body) {
        var input = ArchitectureChildInput.parse(body, false, false);
        requireParents(projectId);
        var component = components.findForUpdate(id, projectId).orElseThrow(ArchitectureMutationException::missingComponent);
        if (component.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("COMPONENT", component.getVersion());
        componentFields(component, input.changes()); em.flush();
        if (component.getVersion() == input.expectedVersion()) {
            if (components.incrementVersionIfCurrent(id, input.expectedVersion()) != 1) {
                Long current = components.currentVersion(id, projectId);
                if (current == null) throw ArchitectureMutationException.missingComponent();
                throw ArchitectureMutationException.version("COMPONENT", current);
            }
            em.refresh(component);
        }
        return reads.cardAfterWrite(projectId);
    }

    @Transactional
    public Card patchIntegration(String projectId, String id, JsonNode body) {
        var input = ArchitectureChildInput.parse(body, false, true);
        requireParents(projectId);
        var integration = integrations.findForUpdate(id, projectId).orElseThrow(ArchitectureMutationException::missingIntegration);
        if (integration.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("INTEGRATION", integration.getVersion());
        integrationFields(integration, input.changes()); em.flush();
        if (integration.getVersion() == input.expectedVersion()) {
            if (integrations.incrementVersionIfCurrent(id, input.expectedVersion()) != 1) {
                Long current = integrations.currentVersion(id, projectId);
                if (current == null) throw ArchitectureMutationException.missingIntegration();
                throw ArchitectureMutationException.version("INTEGRATION", current);
            }
            em.refresh(integration);
        }
        replaceClasses(integration, input.changes()); em.flush();
        return reads.cardAfterWrite(projectId);
    }

    private ArchitecturePassportEntity creationPassport(String projectId, long expected) {
        // Lock order agrees with passport creation and project deletion: project -> passport -> new child.
        projectLock.require(projectId);
        var passport = passports.findForUpdate(projectId).orElseThrow(ArchitectureMutationException::missingPassport);
        if (passport.getVersion() != expected) throw ArchitectureMutationException.version("PASSPORT", passport.getVersion());
        return passport;
    }
    private void requireParents(String projectId) {
        // PATCH never changes ownership. Lock only the existing child, so different records are independent.
        if (!projects.existsById(projectId)) throw ProjectMutationException.projectNotFound();
        if (!passports.existsById(projectId)) throw ArchitectureMutationException.missingPassport();
    }
    private void incrementPassport(ArchitecturePassportEntity passport, long expected) {
        if (passports.incrementVersionIfCurrent(passport.getProjectId(), expected) != 1) {
            Long current = passports.currentVersion(passport.getProjectId());
            if (current == null) throw ArchitectureMutationException.missingPassport();
            throw ArchitectureMutationException.version("PASSPORT", current);
        }
        em.refresh(passport);
    }
    @SuppressWarnings("unchecked")
    private void replaceClasses(ArchitectureIntegrationEntity integration, Map<String, Object> changes) {
        if (changes.containsKey("dataClasses")) classes.replace(integration, (Set<String>) changes.get("dataClasses"));
    }
    private static void componentFields(ArchitectureComponentEntity component, Map<String, Object> changes) {
        changes.forEach((field, value) -> {
            switch (field) {
                case "name" -> component.setName((String) value);
                case "componentType" -> component.setComponentType((String) value);
                case "purpose" -> component.setPurpose((String) value);
                case "platform" -> component.setPlatform((String) value);
                case "criticality" -> component.setCriticality((String) value);
                case "externalComponent" -> component.setExternalComponent((Boolean) value);
                case "position" -> component.setPosition((Integer) value);
                default -> throw new IllegalStateException("Unexpected validated component field");
            }
        });
    }
    private static void integrationFields(ArchitectureIntegrationEntity integration, Map<String, Object> changes) {
        changes.forEach((field, value) -> {
            switch (field) {
                case "name" -> integration.setName((String) value);
                case "sourceSystem" -> integration.setSourceSystem((String) value);
                case "targetSystem" -> integration.setTargetSystem((String) value);
                case "direction" -> integration.setDirection((String) value);
                case "interactionType" -> integration.setInteractionType((String) value);
                case "authCategory" -> integration.setAuthCategory((String) value);
                case "criticality" -> integration.setCriticality((String) value);
                case "readiness" -> integration.setReadiness((String) value);
                case "responsibleParty" -> integration.setResponsibleParty((String) value);
                case "comment" -> integration.setComment((String) value);
                case "dataClasses" -> { /* Handled explicitly after the row flush. */ }
                default -> throw new IllegalStateException("Unexpected validated integration field");
            }
        });
    }
}
