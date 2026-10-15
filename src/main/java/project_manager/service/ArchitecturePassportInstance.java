package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import project_manager.domain.ArchitecturePassportEntity;
import project_manager.web.ArchitectureMutationException;
import java.util.Map;
import java.util.UUID;

/** Durable incarnation token, independent of technical and document versions. */
public final class ArchitecturePassportInstance {
    private static final String FIELD = "expectedPassportInstanceId";
    private ArchitecturePassportInstance() { }
    public static String json(JsonNode body) {
        JsonNode value = body == null ? null : body.get(FIELD);
        if (value == null || !value.isTextual()) throw ArchitectureMutationException.invalid(FIELD);
        return uuid(value.textValue());
    }
    public static String query(Map<String, String[]> parameters) {
        return uuid(ArchitectureChildDeletionInput.single(parameters, FIELD));
    }
    private static String uuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw ArchitectureMutationException.invalid(FIELD);
        return UUID.fromString(value).toString();
    }
    public static void check(ArchitecturePassportEntity passport, String expected) {
        if (!passport.getInstanceId().equals(expected)) throw ArchitectureMutationException.instanceConflict(passport.getVersion());
    }
}
