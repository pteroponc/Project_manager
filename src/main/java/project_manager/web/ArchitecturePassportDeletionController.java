package project_manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitecturePassportDeletionService;
import project_manager.web.dto.ArchitectureReadDtos.Card;

@RestController
@RequestMapping("/api/projects/{id}/architecture")
public class ArchitecturePassportDeletionController {
    private final ArchitecturePassportDeletionService deletions;
    public ArchitecturePassportDeletionController(ArchitecturePassportDeletionService deletions) { this.deletions = deletions; }
    @DeleteMapping
    public Card delete(@PathVariable String id, HttpServletRequest request) {
        return deletions.delete(id, request.getParameterMap());
    }
}
