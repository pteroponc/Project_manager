package project_manager.web;

public class ProjectConflictException extends RuntimeException {
    public ProjectConflictException(String message) {
        super(message);
    }
}
