package project_manager.service;

import project_manager.web.ArchitectureMutationException;
import java.util.Map;
import java.util.Set;

/** Explicit query contract: no defaults, duplicate parameters or implicit confirmation. */
public record ArchitectureChildDeletionInput(long expectedVersion, long expectedPassportVersion, String expectedPassportInstanceId) {
    public static ArchitectureChildDeletionInput parse(Map<String, String[]> parameters) {
        if (parameters == null || !parameters.keySet().equals(Set.of("expectedVersion", "expectedPassportVersion", "confirmed", "expectedPassportInstanceId")))
            throw ArchitectureMutationException.badRequest();
        if (!"true".equals(single(parameters, "confirmed"))) throw ArchitectureMutationException.invalid("confirmed");
        return new ArchitectureChildDeletionInput(version(parameters, "expectedVersion"), version(parameters, "expectedPassportVersion"), ArchitecturePassportInstance.query(parameters));
    }
    static String single(Map<String, String[]> parameters, String field) {
        String[] values = parameters.get(field);
        if (values == null || values.length != 1 || values[0] == null) throw ArchitectureMutationException.invalid(field);
        return values[0];
    }
    static long version(Map<String, String[]> parameters, String field) {
        String value = single(parameters, field);
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw ArchitectureMutationException.invalid(field);
        try {
            long version = Long.parseLong(value);
            if (version == Long.MAX_VALUE) throw ArchitectureMutationException.invalid(field);
            return version;
        } catch (NumberFormatException invalid) { throw ArchitectureMutationException.invalid(field); }
    }
}
