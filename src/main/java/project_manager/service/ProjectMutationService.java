package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectRepository;
import project_manager.web.ProjectMutationException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectMutationService {
    private static final Set<String> FIELDS = Set.of("expectedVersion", "name", "owner", "status", "health",
        "deliveryModel", "progress", "startDate", "quarter", "deadline", "milestone", "risk",
        "dependency", "kpiName", "kpiTarget", "summary");

    private final ProjectRepository projects;
    private final ProjectAssessmentService assessment;

    public ProjectMutationService(ProjectRepository projects, ProjectAssessmentService assessment) {
        this.projects = projects;
        this.assessment = assessment;
    }

    @Transactional
    public void patch(String id, JsonNode body) {
        MutationJson.object(body);
        MutationJson.allowed(body, FIELDS);
        long expected = MutationJson.expectedVersion(body);
        if (body.size() == 1) throw ProjectMutationException.badRequest("At least one project field required");
        ProjectEntity project = projects.findById(id).orElseThrow(ProjectMutationException::projectNotFound);
        if (project.getVersion() != expected) throw ProjectMutationException.versionConflict(project.getVersion());

        Map<String, Object> changes = new HashMap<>();
        body.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            if (field.equals("expectedVersion")) return;
            JsonNode value = entry.getValue();
            Object parsed = switch (field) {
                case "name", "owner", "quarter" -> MutationJson.text(value, field, 255);
                case "milestone", "kpiName", "kpiTarget" -> MutationJson.text(value, field, 1000);
                case "risk", "dependency", "summary" -> MutationJson.text(value, field, 10000);
                case "status" -> supportedStatus(value);
                case "health" -> supportedHealth(value);
                case "deliveryModel" -> deliveryModel(value);
                case "deadline" -> {
                    MutationJson.date(value, field, false);
                    yield value.textValue();
                }
                case "progress" -> MutationJson.integer(value, field, 0, 100, true);
                case "startDate" -> MutationJson.date(value, field, true);
                default -> throw ProjectMutationException.badRequest("Unknown field: " + field);
            };
            changes.put(field, parsed);
        });

        changes.forEach((field, value) -> {
            switch (field) {
                case "name" -> project.setName((String) value);
                case "owner" -> project.setOwner((String) value);
                case "status" -> project.setStatus((String) value);
                case "health" -> project.setHealth((String) value);
                case "deliveryModel" -> project.setDeliveryModel((String) value);
                case "progress" -> project.setProgress((Integer) value);
                case "startDate" -> project.setStartDate((LocalDate) value);
                case "quarter" -> project.setQuarter((String) value);
                case "deadline" -> project.setDeadline((String) value);
                case "milestone" -> project.setMilestone((String) value);
                case "risk" -> project.setRisk((String) value);
                case "dependency" -> project.setDependency((String) value);
                case "kpiName" -> project.setKpiName((String) value);
                case "kpiTarget" -> project.setKpiTarget((String) value);
                case "summary" -> project.setSummary((String) value);
                default -> throw new IllegalStateException("Unexpected validated field");
            }
        });

        projects.flush();
        if (project.getVersion() == expected && projects.incrementVersionIfCurrent(id, expected) != 1) {
            throw ProjectMutationException.versionConflict(projects.currentVersion(id));
        }
    }

    private String supportedStatus(JsonNode value) {
        String status = MutationJson.text(value, "status", 255).toLowerCase(Locale.ROOT);
        if (!assessment.isKnownStatus(status)) throw ProjectMutationException.invalid("status", "Unsupported status");
        return status;
    }

    private String supportedHealth(JsonNode value) {
        String health = MutationJson.text(value, "health", 255).toLowerCase(Locale.ROOT);
        if (!assessment.isKnownHealth(health)) throw ProjectMutationException.invalid("health", "Unsupported health");
        return health;
    }

    private String deliveryModel(JsonNode value) {
        String model = MutationJson.text(value, "deliveryModel", 255).toLowerCase(Locale.ROOT);
        if (!Set.of("kanban", "scrum", "waterfall").contains(model)) {
            throw ProjectMutationException.invalid("deliveryModel", "Unsupported delivery model");
        }
        return model;
    }
}
