package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import project_manager.domain.SecurityRiskEntity;
import project_manager.web.ArchitectureMutationException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static project_manager.service.ArchitecturePassportInput.*;

/** Explicit commands; merged business state is validated before changing a managed entity. */
public record ArchitectureRiskInput(long expectedVersion, String expectedPassportInstanceId, Map<String, Object> changes) {
    private static final List<String> FIELDS = List.of("name", "description", "category", "probability", "impact", "owner",
        "treatmentDueDate", "treatmentPlan", "status", "closedDate", "acceptanceJustification");
    private static final Set<String> REQUIRED = Set.of("name", "category", "probability", "impact", "status");
    private static final Set<String> CATEGORIES = Set.of("ACCESS", "DATA", "AVAILABILITY", "INTEGRATION", "PROCESS", "OTHER");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "ACCEPTED", "MITIGATED", "CLOSED");

    public static ArchitectureRiskInput parse(JsonNode body, boolean create) {
        if (body == null || !body.isObject()) throw ArchitectureMutationException.badRequest();
        String versionField = create ? "expectedPassportVersion" : "expectedVersion";
        body.fieldNames().forEachRemaining(field -> {
            if (!field.equals(versionField) && !(field.equals("expectedPassportInstanceId") && create) && !FIELDS.contains(field)) throw ArchitectureMutationException.badRequest();
        });
        String instance = create ? ArchitecturePassportInstance.json(body) : null;
        long expected = version(body.get(versionField), versionField);
        if (!create && body.size() == 1) throw ArchitectureMutationException.badRequest();
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String field : FIELDS) {
            JsonNode value = body.get(field);
            if (value == null) {
                if (create && REQUIRED.contains(field)) throw ArchitectureMutationException.invalid(field);
                continue;
            }
            if (value.isNull() && REQUIRED.contains(field)) throw ArchitectureMutationException.invalid(field);
            Object parsed = switch (field) {
                case "name", "owner" -> text(value, field, 255, false);
                case "description", "treatmentPlan", "acceptanceJustification" -> text(value, field, 2000, true);
                case "category" -> code(value, field, CATEGORIES);
                case "probability", "impact" -> code(value, field, CRITICALITY);
                case "status" -> code(value, field, STATUSES);
                case "treatmentDueDate", "closedDate" -> date(value, field);
                default -> throw ArchitectureMutationException.badRequest();
            };
            changes.put(field, parsed);
        }
        return new ArchitectureRiskInput(expected, instance, Collections.unmodifiableMap(changes));
    }

    public State merge(SecurityRiskEntity existing) {
        State state = new State((String) changes.getOrDefault("name", existing.getName()),
            (String) changes.getOrDefault("description", existing.getDescription()),
            (String) changes.getOrDefault("category", existing.getCategory()),
            (String) changes.getOrDefault("probability", existing.getProbability()),
            (String) changes.getOrDefault("impact", existing.getImpact()),
            (String) changes.getOrDefault("owner", existing.getOwner()),
            (LocalDate) changes.getOrDefault("treatmentDueDate", existing.getTreatmentDueDate()),
            (String) changes.getOrDefault("treatmentPlan", existing.getTreatmentPlan()),
            (String) changes.getOrDefault("status", existing.getStatus()),
            (LocalDate) changes.getOrDefault("closedDate", existing.getClosedDate()),
            (String) changes.getOrDefault("acceptanceJustification", existing.getAcceptanceJustification()));
        if ("ACCEPTED".equals(state.status()) && (state.acceptanceJustification() == null || state.acceptanceJustification().isBlank()))
            throw ArchitectureMutationException.invalid("acceptanceJustification");
        if ("CLOSED".equals(state.status()) ? state.closedDate() == null : state.closedDate() != null)
            throw ArchitectureMutationException.invalid("closedDate");
        return state;
    }

    public record State(String name, String description, String category, String probability, String impact, String owner,
        LocalDate treatmentDueDate, String treatmentPlan, String status, LocalDate closedDate, String acceptanceJustification) {
        public void applyTo(SecurityRiskEntity entity) {
            entity.setName(name); entity.setDescription(description); entity.setCategory(category);
            entity.setProbability(probability); entity.setImpact(impact); entity.setOwner(owner);
            entity.setTreatmentDueDate(treatmentDueDate); entity.setTreatmentPlan(treatmentPlan); entity.setStatus(status);
            entity.setClosedDate(closedDate); entity.setAcceptanceJustification(acceptanceJustification);
        }
    }
}
