package project_manager.service;

import project_manager.web.ArchitectureMutationException;
import java.util.Map;
import java.util.Set;

public record ArchitecturePassportDeletionInput(long expectedVersion, String expectedPassportInstanceId) {
    public static ArchitecturePassportDeletionInput parse(Map<String, String[]> parameters) {
        if (parameters == null || !parameters.keySet().equals(Set.of("expectedVersion", "confirmed", "expectedPassportInstanceId")))
            throw ArchitectureMutationException.badRequest();
        if (!"true".equals(ArchitectureChildDeletionInput.single(parameters, "confirmed")))
            throw ArchitectureMutationException.invalid("confirmed");
        return new ArchitecturePassportDeletionInput(ArchitectureChildDeletionInput.version(parameters, "expectedVersion"), ArchitecturePassportInstance.query(parameters));
    }
}
