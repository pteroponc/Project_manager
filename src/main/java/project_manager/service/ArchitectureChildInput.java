package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import project_manager.web.ArchitectureMutationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static project_manager.service.ArchitecturePassportInput.*;

/** Component/integration commands reuse B3.1 text, code, set and version validation. */
public record ArchitectureChildInput(long expectedVersion, String expectedPassportInstanceId, Map<String, Object> changes) {
    private static final List<String> COMPONENT_FIELDS = List.of("name", "componentType", "purpose", "platform", "criticality", "externalComponent", "position");
    private static final List<String> INTEGRATION_FIELDS = List.of("name", "sourceSystem", "targetSystem", "direction", "interactionType",
        "authCategory", "criticality", "readiness", "responsibleParty", "comment", "dataClasses");
    private static final Set<String> NULLABLE = Set.of("purpose", "platform", "responsibleParty", "comment");
    private static final Set<String> COMPONENT_TYPES = Set.of("FRONTEND", "BACKEND", "DATABASE", "QUEUE", "EXTERNAL_SERVICE", "INFRASTRUCTURE");
    private static final Set<String> DIRECTIONS = Set.of("INBOUND", "OUTBOUND", "BIDIRECTIONAL");
    private static final Set<String> INTERACTION = Set.of("API", "EVENT", "FILE_EXCHANGE", "BATCH", "OTHER");
    private static final Set<String> AUTH = Set.of("NONE", "SERVICE_ACCOUNT", "OAUTH2", "MTLS", "CERTIFICATE", "OTHER");
    private static final Set<String> READINESS = Set.of("PLANNED", "IN_PROGRESS", "READY", "BLOCKED");

    public static ArchitectureChildInput parse(JsonNode body, boolean create, boolean integration) {
        if (body == null || !body.isObject()) throw ArchitectureMutationException.badRequest();
        List<String> fields = integration ? INTEGRATION_FIELDS : COMPONENT_FIELDS;
        String versionField = create ? "expectedPassportVersion" : "expectedVersion";
        body.fieldNames().forEachRemaining(field -> {
            if (!field.equals(versionField) && !(field.equals("expectedPassportInstanceId") && create) && !fields.contains(field)) throw ArchitectureMutationException.badRequest();
        });
        String instance = create ? ArchitecturePassportInstance.json(body) : null;
        long expected = version(body.get(versionField), versionField);
        if (!create && body.size() == 1) throw ArchitectureMutationException.badRequest();
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String field : fields) {
            JsonNode value = body.get(field);
            if (value == null) {
                if (create && !NULLABLE.contains(field)) throw ArchitectureMutationException.invalid(field);
                continue;
            }
            if (value.isNull() && !NULLABLE.contains(field)) throw ArchitectureMutationException.invalid(field);
            Object parsed = switch (field) {
                case "name", "sourceSystem", "targetSystem", "platform", "responsibleParty" -> text(value, field, 255, false);
                case "purpose", "comment" -> text(value, field, 1000, true);
                case "criticality" -> code(value, field, CRITICALITY);
                case "componentType" -> code(value, field, COMPONENT_TYPES);
                case "direction" -> code(value, field, DIRECTIONS);
                case "interactionType" -> code(value, field, INTERACTION);
                case "authCategory" -> code(value, field, AUTH);
                case "readiness" -> code(value, field, READINESS);
                case "externalComponent" -> {
                    if (!value.isBoolean()) throw ArchitectureMutationException.invalid(field);
                    yield value.booleanValue();
                }
                case "position" -> {
                    if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) throw ArchitectureMutationException.invalid(field);
                    yield value.intValue();
                }
                case "dataClasses" -> {
                    Set<String> result = codes(value, field, DATA);
                    if (result.isEmpty()) throw ArchitectureMutationException.invalid(field);
                    yield result;
                }
                default -> throw ArchitectureMutationException.badRequest();
            };
            changes.put(field, parsed);
        }
        return new ArchitectureChildInput(expected, instance, Collections.unmodifiableMap(changes));
    }
}
