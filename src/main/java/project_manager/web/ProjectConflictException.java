package project_manager.web;

public class ProjectConflictException extends RuntimeException {
    private final String code;

    public ProjectConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
