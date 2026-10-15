package project_manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitectureCheckMutationService;
import project_manager.web.dto.ArchitectureReadDtos.Card;

@RestController
@RequestMapping("/api/projects/{id}/architecture/checks")
public class ArchitectureCheckMutationController {
    private final ArchitectureCheckMutationService mutations;
    private final ArchitectureMutationJson json;
    public ArchitectureCheckMutationController(ArchitectureCheckMutationService mutations, ArchitectureMutationJson json) {
        this.mutations = mutations; this.json = json;
    }
    @PostMapping(value = "/initialize", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Card initialize(@PathVariable String id, HttpServletRequest request) { return mutations.initialize(id, json.read(request)); }

    @PatchMapping(value = "/{recordId}", consumes = "application/json")
    public Card patch(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return mutations.patch(id, recordId, json.read(request));
    }
}
