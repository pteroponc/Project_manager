package project_manager.testsupport;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Set;

@TestConfiguration
public class BrowserWriteMockMvcConfiguration {
    @Bean
    MockMvcBuilderCustomizer browserWriteMarker() {
        Set<String> writes = Set.of("POST", "PUT", "PATCH", "DELETE");
        // MVC regression clients represent the normal frontend. The security tests use a raw client.
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(request -> {
            if (writes.contains(request.getMethod())) request.addHeader("X-PM-Write", "1");
            return request;
        }));
    }
}
