package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectManagerApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointReportsReady() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void projectAndBoardCardLifecycleWorks() throws Exception {
        String projectId = projectIdFrom(createProject());

        mockMvc.perform(get("/api/projects/{id}", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Release verification"));

        mockMvc.perform(get("/api/portfolio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectCount").isNumber());

        String cardId = cardIdFrom(createCard(projectId, "backlog", "medium", 3));

        mockMvc.perform(put("/api/projects/{projectId}/board/cards/{cardId}", projectId, cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cardRequest("in-progress", "high", 5)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(cardId))
            .andExpect(jsonPath("$.priority").value("high"))
            .andExpect(jsonPath("$.estimate").value(5));

        mockMvc.perform(get("/api/projects/{id}/board", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns[2].key").value("in-progress"))
            .andExpect(jsonPath("$.columns[2].cards[*].id").value(hasItem(cardId)));
    }

    @Test
    void invalidRequestsReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequest(" ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("name")));

        mockMvc.perform(post("/api/projects/missing/board/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cardRequest("backlog", "medium", -1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("estimate")));
    }

    @Test
    void projectCanBeCreatedWithoutBudgetOrProgressAndZeroRemainsZero() throws Exception {
        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequestWithoutMetrics("No metrics")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.budget").value(nullValue()))
            .andExpect(jsonPath("$.progress").value(nullValue()))
            .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequest("Real zero").replace("100000", "0").replace("\"progress\": 10", "\"progress\": 0")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.budget").value(0))
            .andExpect(jsonPath("$.progress").value(0));
    }

    @Test
    void legacyPutWithoutOptionalFieldsPreservesStoredBudgetProgressAndStartDate() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequest("Preserved metrics").replace(
                    "\"progress\": 10,", "\"progress\": 10,\n  \"startDate\": \"2026-09-01\",")))
            .andExpect(status().isCreated())
            .andReturn();
        String projectId = projectIdFrom(created);

        mockMvc.perform(put("/api/projects/{id}", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequestWithoutMetrics("Updated without metrics")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated without metrics"))
            .andExpect(jsonPath("$.budget").value(100000))
            .andExpect(jsonPath("$.progress").value(10))
            .andExpect(jsonPath("$.startDate").value("2026-09-01"));

        String explicitUpdate = projectRequest("Explicit metric update")
            .replace("100000", "250000")
            .replace("\"progress\": 10,", "\"progress\": 35,\n  \"startDate\": \"2026-10-01\",");
        mockMvc.perform(put("/api/projects/{id}", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(explicitUpdate))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budget").value(250000))
            .andExpect(jsonPath("$.progress").value(35))
            .andExpect(jsonPath("$.startDate").value("2026-10-01"));

        String explicitNulls = projectRequestWithoutMetrics("Explicit nulls are not clearing")
            .replace("\"quarter\":", "\"budget\": null,\n  \"progress\": null,\n  \"startDate\": null,\n  \"quarter\":");
        mockMvc.perform(put("/api/projects/{id}", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(explicitNulls))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budget").value(250000))
            .andExpect(jsonPath("$.progress").value(35))
            .andExpect(jsonPath("$.startDate").value("2026-10-01"));
    }

    private MvcResult createProject() throws Exception {
        return mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequest("Release verification")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Release verification"))
            .andReturn();
    }

    private MvcResult createCard(String projectId, String columnKey, String priority, int estimate) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/board/cards", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cardRequest(columnKey, priority, estimate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Verify release"))
            .andReturn();
    }

    private String projectIdFrom(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("id").asText();
    }

    private String cardIdFrom(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("id").asText();
    }

    private String projectRequest(String name) {
        return """
            {
              "name": "%s",
              "owner": "Release team",
              "status": "planned",
              "health": "green",
              "deliveryModel": "kanban",
              "budget": 100000,
              "progress": 10,
              "quarter": "Q3 2026",
              "deadline": "2026-12-31",
              "milestone": "Release gate",
              "risk": "No material risks",
              "dependency": "CI pipeline",
              "kpiName": "Release readiness",
              "kpiTarget": "100%%",
              "summary": "Project created by the release verification suite."
            }
            """.formatted(name);
    }

    private String projectRequestWithoutMetrics(String name) {
        return """
            {
              "name": "%s",
              "owner": "Release team",
              "status": "planned",
              "health": "green",
              "deliveryModel": "kanban",
              "quarter": "Q3 2026",
              "deadline": "2026-12-31",
              "milestone": "Release gate",
              "risk": "No material risks",
              "dependency": "CI pipeline",
              "kpiName": "Release readiness",
              "kpiTarget": "100%%",
              "summary": "Project created without optional metrics."
            }
            """.formatted(name);
    }

    private String cardRequest(String columnKey, String priority, int estimate) {
        return """
            {
              "title": "Verify release",
              "description": "Exercise the board API before release.",
              "owner": "Release team",
              "dueDate": "2026-12-31",
              "columnKey": "%s",
              "priority": "%s",
              "labels": "release, verification",
              "estimate": %d,
              "blocked": false
            }
            """.formatted(columnKey, priority, estimate);
    }
}
