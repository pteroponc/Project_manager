package project_manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitectureChildMutationService;
import project_manager.web.dto.ArchitectureReadDtos.Card;

@RestController
@RequestMapping("/api/projects/{id}/architecture")
public class ArchitectureChildMutationController {
    private final ArchitectureChildMutationService mutations;
    private final ArchitectureMutationJson json;
    public ArchitectureChildMutationController(ArchitectureChildMutationService mutations, ArchitectureMutationJson json) {
        this.mutations = mutations; this.json = json;
    }
    @PostMapping(value = "/components", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Card createComponent(@PathVariable String id, HttpServletRequest request) { return mutations.createComponent(id, json.read(request)); }

    @PatchMapping(value = "/components/{recordId}", consumes = "application/json")
    public Card patchComponent(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return mutations.patchComponent(id, recordId, json.read(request));
    }
    @PostMapping(value = "/integrations", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Card createIntegration(@PathVariable String id, HttpServletRequest request) { return mutations.createIntegration(id, json.read(request)); }

    @PatchMapping(value = "/integrations/{recordId}", consumes = "application/json")
    public Card patchIntegration(@PathVariable String id, @PathVariable String recordId, HttpServletRequest request) {
        return mutations.patchIntegration(id, recordId, json.read(request));
    }
}
