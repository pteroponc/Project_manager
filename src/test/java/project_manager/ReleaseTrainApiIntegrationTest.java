package project_manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_release_crud;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ReleaseTrainApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private Map<String, Object> request() {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", "CRUD review train");
        body.put("status", "planning");
        body.put("cadence", "2 weeks");
        body.put("quarter", "Q4 2026");
        body.put("plannedReleaseDate", "2026-12-20");
        body.put("codeFreezeDate", "2026-12-01");
        body.put("qaFreezeDate", "2026-12-10");
        body.put("goLiveDate", "2026-12-21");
        body.put("capacityPoints", 60);
        body.put("committedPoints", 40);
        body.put("readiness", 50);
        body.put("blockedItems", 0);
        body.put("scope", "Free text, not a project reference");
        body.put("risk", "Review risk");
        body.put("decision", "Review decision");
        return body;
    }

    @Test
    void fullCrudAndSnapshotUseTheExistingContract() throws Exception {
        var body = request();
        var created = mvc.perform(post("/api/release-trains").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)))
            .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/release-trains/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value(body.get("scope")));
        body.put("name", "Updated review train");
        body.put("status", "released");
        body.put("readiness", 100);
        mvc.perform(put("/api/release-trains/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.name").value("Updated review train"));
        mvc.perform(get("/api/release-trains/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("released"));
        mvc.perform(get("/api/release-trains/snapshot")).andExpect(status().isOk())
            .andExpect(jsonPath("$.trains[*].id").value(hasItem(id)));
        mvc.perform(delete("/api/release-trains/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/release-trains/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void missingGetPutAndDeleteReturn404() throws Exception {
        mvc.perform(get("/api/release-trains/missing-review")).andExpect(status().isNotFound());
        mvc.perform(put("/api/release-trains/missing-review").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request())))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/release-trains/missing-review")).andExpect(status().isNotFound());
    }

    @Test
    void requiredTextAndIntegerBoundsAreValidatedForPostAndPut() throws Exception {
        for (String field : new String[]{"scope", "name", "risk", "decision"}) {
            var body = request(); body.put(field, " ");
            mvc.perform(post("/api/release-trains").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        }
        for (var invalid : Map.of("readiness", 101, "blockedItems", -1, "capacityPoints", -1).entrySet()) {
            var body = request(); body.put(invalid.getKey(), invalid.getValue());
            mvc.perform(put("/api/release-trains/missing-review").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        }
    }
}
