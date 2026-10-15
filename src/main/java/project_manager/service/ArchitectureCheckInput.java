package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import project_manager.domain.ArchitectureCheckEntity;
import project_manager.web.ArchitectureMutationException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static project_manager.service.ArchitecturePassportInput.*;

/** Parses explicit commands, then validates a merged state before touching the managed entity. */
public record ArchitectureCheckInput(long expectedVersion, Map<String, Object> changes) {
    private static final List<String> FIELDS = List.of("required", "status", "owner", "plannedDate", "actualDate", "comment");
    private static final Set<String> STATUSES = Set.of("NOT_REQUIRED", "NOT_STARTED", "IN_PROGRESS", "IN_REVIEW", "PASSED", "CHANGES_REQUIRED", "BLOCKED");

    public static long initializationVersion(JsonNode body) {
        object(body);
        if (body.size() != 1 || !body.has("expectedPassportVersion")) throw ArchitectureMutationException.badRequest();
        return version(body.get("expectedPassportVersion"), "expectedPassportVersion");
    }
    public static ArchitectureCheckInput patch(JsonNode body) {
        object(body);
        body.fieldNames().forEachRemaining(field -> {
            if (!field.equals("expectedVersion") && !FIELDS.contains(field)) throw ArchitectureMutationException.badRequest();
        });
        long expected = version(body.get("expectedVersion"), "expectedVersion");
        if (body.size() == 1) throw ArchitectureMutationException.badRequest();
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String field : FIELDS) {
            JsonNode value = body.get(field);
            if (value == null) continue;
            Object parsed = switch (field) {
                case "required" -> {
                    if (!value.isBoolean()) throw ArchitectureMutationException.invalid(field);
                    yield value.booleanValue();
                }
                case "status" -> code(value, field, STATUSES);
                case "owner" -> text(value, field, 255, false);
                case "comment" -> text(value, field, 2000, true);
                case "plannedDate", "actualDate" -> date(value, field);
                default -> throw ArchitectureMutationException.badRequest();
            };
            changes.put(field, parsed);
        }
        return new ArchitectureCheckInput(expected, Collections.unmodifiableMap(changes));
    }
    private static void object(JsonNode body) {
        if (body == null || !body.isObject()) throw ArchitectureMutationException.badRequest();
    }
    public State merge(ArchitectureCheckEntity existing) {
        State result = new State((Boolean) changes.getOrDefault("required", existing.getRequired()),
            (String) changes.getOrDefault("status", existing.getStatus()), (String) changes.getOrDefault("owner", existing.getOwner()),
            (LocalDate) changes.getOrDefault("plannedDate", existing.getPlannedDate()),
            (LocalDate) changes.getOrDefault("actualDate", existing.getActualDate()), (String) changes.getOrDefault("comment", existing.getComment()));
        if (result.required() && "NOT_REQUIRED".equals(result.status())) throw ArchitectureMutationException.invalid("status");
        if (!result.required()) {
            if (!"NOT_REQUIRED".equals(result.status())) throw ArchitectureMutationException.invalid("status");
            if (result.comment() == null || result.comment().isBlank()) throw ArchitectureMutationException.invalid("comment");
        }
        if ("PASSED".equals(result.status()) ? result.actualDate() == null : result.actualDate() != null)
            throw ArchitectureMutationException.invalid("actualDate");
        return result;
    }
    public record State(boolean required, String status, String owner, LocalDate plannedDate, LocalDate actualDate, String comment) {
        public void applyTo(ArchitectureCheckEntity entity) {
            entity.setRequired(required); entity.setStatus(status); entity.setOwner(owner);
            entity.setPlannedDate(plannedDate); entity.setActualDate(actualDate); entity.setComment(comment);
        }
    }
}
