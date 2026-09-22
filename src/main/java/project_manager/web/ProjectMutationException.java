package project_manager.web;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ProjectMutationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;
    private final Long currentVersion;

    private ProjectMutationException(HttpStatus status, String code, String message,
                                     Map<String, String> fieldErrors, Long currentVersion) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
        this.currentVersion = currentVersion;
    }

    public static ProjectMutationException badRequest(String message) {
        return new ProjectMutationException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, null, null);
    }

    public static ProjectMutationException invalid(String field, String message) {
        return new ProjectMutationException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request fields",
            Map.of(field, message), null);
    }

    public static ProjectMutationException projectNotFound() {
        return new ProjectMutationException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found", null, null);
    }

    public static ProjectMutationException milestoneNotFound() {
        return new ProjectMutationException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found", null, null);
    }

    public static ProjectMutationException versionConflict(long currentVersion) {
        return new ProjectMutationException(HttpStatus.CONFLICT, "PROJECT_VERSION_CONFLICT",
            "Project version does not match", null, currentVersion);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public Long getCurrentVersion() { return currentVersion; }
}
