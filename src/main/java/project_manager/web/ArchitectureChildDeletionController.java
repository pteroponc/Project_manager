package project_manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitectureChildDeletionService;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import static project_manager.service.ArchitectureChildDeletionService.Kind.*;

/** Four explicit routes; no arbitrary collection path dispatch. */
@RestController
@RequestMapping("/api/projects/{id}/architecture")
public class ArchitectureChildDeletionController {
    private final ArchitectureChildDeletionService deletions;
    public ArchitectureChildDeletionController(ArchitectureChildDeletionService deletions) { this.deletions = deletions; }

    @DeleteMapping("/components/{recordId}")
    public Card component(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return deletions.delete(id, recordId, COMPONENT, request.getParameterMap());
    }
    @DeleteMapping("/integrations/{recordId}")
    public Card integration(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return deletions.delete(id, recordId, INTEGRATION, request.getParameterMap());
    }
    @DeleteMapping("/checks/{recordId}")
    public Card check(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return deletions.delete(id, recordId, CHECK, request.getParameterMap());
    }
    @DeleteMapping("/risks/{recordId}")
    public Card risk(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return deletions.delete(id, recordId, RISK, request.getParameterMap());
    }
}
