package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ArchitecturePassportEntity;
import project_manager.repository.ArchitecturePassportRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.ArchitectureMutationException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Service
public class ArchitecturePassportMutationService {
    private final ProjectWriteLock projectLock;
    private final ProjectRepository projects;
    private final ArchitecturePassportRepository passports;
    private final ArchitecturePassportSetWriter sets;
    private final ArchitectureReadService reads;
    private final EntityManager em;
    private final Clock clock;

    public ArchitecturePassportMutationService(ProjectWriteLock projectLock, ProjectRepository projects,
        ArchitecturePassportRepository passports, ArchitecturePassportSetWriter sets, ArchitectureReadService reads,
        EntityManager em, @Qualifier("architectureAssessmentClock") Clock clock) {
        this.projectLock = projectLock; this.projects = projects; this.passports = passports;
        this.sets = sets; this.reads = reads; this.em = em; this.clock = clock;
    }

    @Transactional
    public Card create(String id, JsonNode body) {
        var input = ArchitecturePassportInput.parse(body, true);
        var project = projectLock.require(id);
        passports.findById(id).ifPresent(existing -> { throw ArchitectureMutationException.exists(existing.getVersion()); });
        if (project.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("PROJECT", project.getVersion());
        var passport = new ArchitecturePassportEntity();
        passport.setProject(project); passport.setProjectId(id);
        passport.setInstanceId(java.util.UUID.randomUUID().toString());
        passport.setVersion(0L); passport.setCreatedAt(clock.instant()); passport.setUpdatedAt(passport.getCreatedAt());
        fields(passport, input.changes());
        em.persist(passport); em.flush();
        if (projects.incrementVersionIfCurrent(id, input.expectedVersion()) != 1) {
            Long current = projects.currentVersion(id);
            if (current == null) throw ProjectMutationException.projectNotFound();
            throw ArchitectureMutationException.version("PROJECT", current);
        }
        em.refresh(project);
        replaceSets(passport, input.changes());
        em.flush();
        return reads.cardAfterWrite(id);
    }

    @Transactional
    public Card patch(String id, JsonNode body) {
        var input = ArchitecturePassportInput.parse(body, false);
        if (!projects.existsById(id)) throw ProjectMutationException.projectNotFound();
        var passport = passports.findForUpdate(id).orElseThrow(ArchitectureMutationException::missingPassport);
        ArchitecturePassportInstance.check(passport, input.expectedPassportInstanceId());
        if (passport.getVersion() != input.expectedVersion()) throw ArchitectureMutationException.version("PASSPORT", passport.getVersion());
        fields(passport, input.changes());
        passport.setUpdatedAt(clock.instant());
        em.flush();
        // Hibernate @Version increments dirty rows. A fixed Clock and an identical patch can be clean.
        // In that case increment once explicitly, then refresh to keep the managed version consistent.
        if (passport.getVersion() == input.expectedVersion()) {
            if (passports.incrementVersionIfCurrent(id, input.expectedVersion()) != 1) {
                Long current = passports.currentVersion(id);
                if (current == null) throw ArchitectureMutationException.missingPassport();
                throw ArchitectureMutationException.version("PASSPORT", current);
            }
            em.refresh(passport);
        }
        replaceSets(passport, input.changes());
        em.flush();
        return reads.cardAfterWrite(id);
    }

    @SuppressWarnings("unchecked") // Values come only from the closed typed parser above.
    private void replaceSets(ArchitecturePassportEntity passport, Map<String, Object> changes) {
        if (changes.containsKey("dataClasses")) sets.dataClasses(passport, (Set<String>) changes.get("dataClasses"));
        if (changes.containsKey("environments")) sets.environments(passport, (Set<String>) changes.get("environments"));
    }
    private static void fields(ArchitecturePassportEntity passport, Map<String, Object> changes) {
        changes.forEach((field, value) -> {
            switch (field) {
                case "criticality" -> passport.setCriticality((String) value);
                case "stage" -> passport.setStage((String) value);
                case "solutionType" -> passport.setSolutionType((String) value);
                case "architect" -> passport.setArchitect((String) value);
                case "solutionSummary" -> passport.setSolutionSummary((String) value);
                case "availabilityRequirement" -> passport.setAvailabilityRequirement((String) value);
                case "recoveryRequirement" -> passport.setRecoveryRequirement((String) value);
                case "lastReviewDate" -> passport.setLastReviewDate((LocalDate) value);
                default -> { /* Sets are replaced separately, within this same transaction. */ }
            }
        });
    }
}
