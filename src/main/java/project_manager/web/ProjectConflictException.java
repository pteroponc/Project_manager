package project_manager.web;

public class ProjectConflictException extends RuntimeException {
    private final String code;
    private final project_manager.web.dto.ProjectDeletionImpactResponse impact;

    public ProjectConflictException(String code, String message) {
        this(code,message,null);
    }
    public ProjectConflictException(String code, String message, project_manager.web.dto.ProjectDeletionImpactResponse impact) {
        super(message); this.code=code; this.impact=impact;
    }
    public project_manager.web.dto.ProjectDeletionImpactResponse getImpact() { return impact; }

    public String getCode() {
        return code;
    }
}
