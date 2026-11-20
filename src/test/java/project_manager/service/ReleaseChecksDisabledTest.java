package project_manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import project_manager.web.ReleaseChecksController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ReleaseChecksDisabledTest {
    @Test
    void endpointAndRefreshReturnUnavailableWithoutCreatingAnHttpClient() throws Exception {
        var service = new ReleaseChecksService(new ObjectMapper(), "example/repository", false, () -> {
            throw new AssertionError("Disabled release checks attempted to create an HTTP client");
        });
        var mvc = standaloneSetup(new ReleaseChecksController(service)).build();

        for (var request : java.util.List.of(get("/api/release-checks"),
                get("/api/release-checks").param("refresh", "true"))) {
            mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value("Проверки сборки и релиза отключены."))
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.latestRelease").doesNotExist())
                .andExpect(jsonPath("$.checks").isEmpty());
        }
    }
}
