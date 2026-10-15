package project_manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitecturePassportMutationService;
import project_manager.web.dto.ArchitectureReadDtos.Card;

@RestController
@RequestMapping("/api/projects/{id}/architecture")
public class ArchitecturePassportMutationController {
    public static final int MAX_BODY_BYTES = ArchitectureMutationJson.MAX_BODY_BYTES;
    private final ArchitecturePassportMutationService mutations;
    private final ArchitectureMutationJson json;
    public ArchitecturePassportMutationController(ArchitecturePassportMutationService mutations, ArchitectureMutationJson json) {
        this.mutations = mutations; this.json = json;
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Card create(@PathVariable String id, HttpServletRequest request) { return mutations.create(id, json.read(request)); }

    @PatchMapping(consumes = "application/json")
    public Card patch(@PathVariable String id, HttpServletRequest request) { return mutations.patch(id, json.read(request)); }
}
