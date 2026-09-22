package project_manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import project_manager.repository.ProjectRepository;
import project_manager.web.dto.ProjectMutationError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final ProjectRepository projects;

    public ApiExceptionHandler(ProjectRepository projects) {
        this.projects = projects;
    }

    @ExceptionHandler(ProjectMutationException.class)
    public ResponseEntity<ProjectMutationError> handleMutation(ProjectMutationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ProjectMutationError(
            exception.getCode(), exception.getMessage(), exception.getFieldErrors(), exception.getCurrentVersion()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProjectMutationError handleUnreadableBody(HttpMessageNotReadableException exception) {
        return new ProjectMutationError("BAD_REQUEST", "Invalid JSON request body", null, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(BadRequestException exception) {
        return Map.of("code", "BAD_REQUEST", "error", exception.getMessage());
    }

    @ExceptionHandler(ProjectConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(ProjectConflictException exception) {
        return Map.of("code", exception.getCode(), "error", exception.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProjectMutationError handleOptimisticConflict(ObjectOptimisticLockingFailureException exception,
                                                         HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        Long currentVersion = segments.length > 3 && "projects".equals(segments[2])
            ? projects.currentVersion(segments[3]) : null;
        return new ProjectMutationError("PROJECT_VERSION_CONFLICT", "Project was changed by another request",
            null, currentVersion);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Validation failed");
        return Map.of("error", message);
    }
}
