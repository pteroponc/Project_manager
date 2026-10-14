package project_manager.web;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitecturePassportMutationService;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.io.IOException;

@RestController
@RequestMapping("/api/projects/{id}/architecture")
public class ArchitecturePassportMutationController {
    public static final int MAX_BODY_BYTES = 32 * 1024;
    private final ArchitecturePassportMutationService mutations;
    // Separate mapper: duplicate/type/size rules must not change existing project APIs.
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(10000).maxNumberLength(20).build())
        .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ArchitecturePassportMutationController(ArchitecturePassportMutationService mutations) { this.mutations = mutations; }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Card create(@PathVariable String id, HttpServletRequest request) { return mutations.create(id, body(request)); }

    @PatchMapping(consumes = "application/json")
    public Card patch(@PathVariable String id, HttpServletRequest request) { return mutations.patch(id, body(request)); }

    private JsonNode body(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) throw ArchitectureMutationException.badRequest();
            return json.readTree(bytes);
        } catch (IOException invalidJson) { throw ArchitectureMutationException.badRequest(); }
    }
}
