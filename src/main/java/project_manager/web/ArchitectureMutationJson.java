package project_manager.web;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.io.IOException;

/** Shared bounded strict parser for architecture writes only; legacy APIs keep their mapper. */
@Component
public class ArchitectureMutationJson {
    public static final int MAX_BODY_BYTES = 32 * 1024;
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(10000).maxNumberLength(20).build())
        .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public JsonNode read(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) throw ArchitectureMutationException.badRequest();
            return json.readTree(bytes);
        } catch (IOException invalidJson) { throw ArchitectureMutationException.badRequest(); }
    }
}
