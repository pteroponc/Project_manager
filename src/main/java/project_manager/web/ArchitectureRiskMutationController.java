package project_manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitectureRiskMutationService;
import project_manager.web.dto.ArchitectureReadDtos.Card;

@RestController
@RequestMapping("/api/projects/{id}/architecture/risks")
public class ArchitectureRiskMutationController {
    private final ArchitectureRiskMutationService mutations;
    private final ArchitectureMutationJson json;
    public ArchitectureRiskMutationController(ArchitectureRiskMutationService mutations, ArchitectureMutationJson json) {
        this.mutations = mutations; this.json = json;
    }
    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Card create(@PathVariable String id, HttpServletRequest request) { return mutations.create(id, json.read(request)); }

    @PatchMapping(value = "/{recordId}", consumes = "application/json")
    public Card patch(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return mutations.patch(id, recordId, json.read(request));
    }
}
