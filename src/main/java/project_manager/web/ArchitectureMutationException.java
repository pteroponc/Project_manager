package project_manager.web;

import org.springframework.http.HttpStatus;
import java.util.Map;

/** Safe errors for passport writes; never retains rejected input or database exceptions. */
public class ArchitectureMutationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;
    private final Long currentVersion;
    private final String versionResource;

    private ArchitectureMutationException(HttpStatus status, String code, String message,
        Map<String, String> fields, Long version, String resource) {
        super(message);
        this.status = status; this.code = code; this.fieldErrors = fields;
        this.currentVersion = version; this.versionResource = resource;
    }
    public static ArchitectureMutationException invalid(String field) {
        return new ArchitectureMutationException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid passport fields",
            Map.of(field, "Invalid value"), null, null);
    }
    public static ArchitectureMutationException badRequest() {
        return new ArchitectureMutationException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid passport request", null, null, null);
    }
    public static ArchitectureMutationException missingPassport() {
        return new ArchitectureMutationException(HttpStatus.NOT_FOUND, "PASSPORT_NOT_FOUND", "Passport not found", null, null, null);
    }
    public static ArchitectureMutationException missingComponent() {
        return new ArchitectureMutationException(HttpStatus.NOT_FOUND, "COMPONENT_NOT_FOUND", "Component not found", null, null, null);
    }
    public static ArchitectureMutationException missingIntegration() {
        return new ArchitectureMutationException(HttpStatus.NOT_FOUND, "INTEGRATION_NOT_FOUND", "Integration not found", null, null, null);
    }
    public static ArchitectureMutationException exists(long version) {
        return new ArchitectureMutationException(HttpStatus.CONFLICT, "PASSPORT_ALREADY_EXISTS", "Passport already exists", null, version, "PASSPORT");
    }
    public static ArchitectureMutationException version(String resource, long version) {
        return new ArchitectureMutationException(HttpStatus.CONFLICT, resource + "_VERSION_CONFLICT",
            "Version does not match", null, version, resource);
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
    public Long currentVersion() { return currentVersion; }
    public String versionResource() { return versionResource; }
}
