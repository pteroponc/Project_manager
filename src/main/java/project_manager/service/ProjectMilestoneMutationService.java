package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ProjectEntity;
import project_manager.domain.ProjectMilestoneEntity;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.ProjectMutationException;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectMilestoneMutationService {
    private static final Set<String> FIELDS = Set.of("expectedVersion", "name", "plannedDate", "completed",
        "completedDate", "position");
    private final ProjectRepository projects;
    private final ProjectMilestoneRepository milestones;

    public ProjectMilestoneMutationService(ProjectRepository projects, ProjectMilestoneRepository milestones) {
        this.projects = projects;
        this.milestones = milestones;
    }

    @Transactional
    public void create(String projectId, JsonNode body) {
        prepare(body);
        long expected = MutationJson.expectedVersion(body);
        ProjectEntity project = project(projectId);
        MilestoneValues values = values(body, null);
        checkVersion(project, expected);
        bump(projectId, expected);

        ProjectMilestoneEntity milestone = new ProjectMilestoneEntity();
        milestone.setId(UUID.randomUUID().toString());
        milestone.setProject(project);
        apply(milestone, values);
        milestones.saveAndFlush(milestone);
    }

    @Transactional
    public void patch(String projectId, String milestoneId, JsonNode body) {
        prepare(body);
        long expected = MutationJson.expectedVersion(body);
        if (body.size() == 1) throw ProjectMutationException.badRequest("At least one milestone field required");
        ProjectEntity project = project(projectId);
        ProjectMilestoneEntity milestone = owned(projectId, milestoneId);
        MilestoneValues values = values(body, milestone);
        checkVersion(project, expected);
        bump(projectId, expected);
        apply(milestone, values);
        milestones.flush();
    }

    @Transactional
    public void delete(String projectId, String milestoneId, String expectedVersion) {
        long expected = MutationJson.expectedVersion(expectedVersion);
        ProjectEntity project = project(projectId);
        ProjectMilestoneEntity milestone = owned(projectId, milestoneId);
        checkVersion(project, expected);
        bump(projectId, expected);
        milestones.delete(milestone);
        milestones.flush();
    }

    private void prepare(JsonNode body) {
        MutationJson.object(body);
        MutationJson.allowed(body, FIELDS);
    }

    private ProjectEntity project(String id) {
        return projects.findById(id).orElseThrow(ProjectMutationException::projectNotFound);
    }

    private ProjectMilestoneEntity owned(String projectId, String milestoneId) {
        ProjectMilestoneEntity milestone = milestones.findById(milestoneId)
            .orElseThrow(ProjectMutationException::milestoneNotFound);
        if (!projectId.equals(milestone.getProjectId())) throw ProjectMutationException.milestoneNotFound();
        return milestone;
    }

    private void checkVersion(ProjectEntity project, long expected) {
        if (project.getVersion() != expected) throw ProjectMutationException.versionConflict(project.getVersion());
    }

    private void bump(String id, long expected) {
        if (projects.incrementVersionIfCurrent(id, expected) != 1) {
            Long currentVersion = projects.currentVersion(id);
            if (currentVersion == null) throw ProjectMutationException.projectNotFound();
            throw ProjectMutationException.versionConflict(currentVersion);
        }
    }

    private MilestoneValues values(JsonNode body, ProjectMilestoneEntity previous) {
        String name = previous == null ? null : previous.getName();
        LocalDate plannedDate = previous == null ? null : previous.getPlannedDate();
        boolean completed = previous != null && previous.isCompleted();
        LocalDate completedDate = previous == null ? null : previous.getCompletedDate();
        Integer position = previous == null ? null : previous.getPosition();

        if (body.has("name")) name = MutationJson.text(body.get("name"), "name", 255);
        if (body.has("plannedDate")) plannedDate = MutationJson.date(body.get("plannedDate"), "plannedDate", false);
        if (body.has("completed")) completed = MutationJson.bool(body.get("completed"), "completed");
        if (body.has("completedDate")) completedDate = MutationJson.date(body.get("completedDate"), "completedDate", true);
        if (body.has("position")) position = MutationJson.integer(body.get("position"), "position", 0,
            Integer.MAX_VALUE, false);

        if (name == null) throw ProjectMutationException.invalid("name", "Non-empty name required");
        if (plannedDate == null) throw ProjectMutationException.invalid("plannedDate", "Planned date required");
        if (position == null) throw ProjectMutationException.invalid("position", "Non-negative position required");
        if (completed && completedDate == null) {
            throw ProjectMutationException.invalid("completedDate", "Completed date required when completed");
        }
        if (!completed && completedDate != null) {
            throw ProjectMutationException.invalid("completedDate", "Date must be null when incomplete");
        }
        return new MilestoneValues(name, plannedDate, completed, completedDate, position);
    }

    private void apply(ProjectMilestoneEntity milestone, MilestoneValues values) {
        milestone.setName(values.name());
        milestone.setPlannedDate(values.plannedDate());
        milestone.setCompleted(values.completed());
        milestone.setCompletedDate(values.completedDate());
        milestone.setPosition(values.position());
    }

    private record MilestoneValues(String name, LocalDate plannedDate, boolean completed,
                                   LocalDate completedDate, int position) { }
}
