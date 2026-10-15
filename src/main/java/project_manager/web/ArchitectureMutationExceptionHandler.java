package project_manager.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {ArchitecturePassportMutationController.class, ArchitectureChildMutationController.class,
    ArchitectureCheckMutationController.class})
public class ArchitectureMutationExceptionHandler {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(String code, String error, Map<String, String> fieldErrors, Long currentVersion, String versionResource) { }

    @ExceptionHandler(ArchitectureMutationException.class)
    public ResponseEntity<Error> handle(ArchitectureMutationException error) {
        return ResponseEntity.status(error.status()).body(new Error(error.code(), error.getMessage(), error.fieldErrors(),
            error.currentVersion(), error.versionResource()));
    }

    @ExceptionHandler(jakarta.persistence.PersistenceException.class)
    public ResponseEntity<Error> handlePersistenceFailure(jakarta.persistence.PersistenceException error) {
        return ResponseEntity.internalServerError().body(new Error("DATABASE_ERROR", "Database operation failed", null, null, null));
    }
}
