package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_board_validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BoardCardValidationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void requiredFieldsRejectMissingNullEmptyAndWhitespaceWithoutCreatingRows() throws Exception {
        String project = project();
        for (String field : List.of("title", "description", "owner", "dueDate", "columnKey", "priority")) {
            for (int variant = 0; variant < 4; variant++) {
                ObjectNode body = card();
                if (variant == 0) body.remove(field);
                if (variant == 1) body.putNull(field);
                if (variant == 2) body.put(field, "");
                if (variant == 3) body.put(field, "   ");
                invalidPost(project, body, field);
            }
        }
        assertThat(cardCount(project)).isZero();
    }

    @Test
    void schemaStringBoundariesAcceptMaximumAndRejectOverflow() throws Exception {
        String project = project();
        for (var entry : Map.of("title", 255, "description", 10000, "owner", 255, "labels", 255).entrySet()) {
            ObjectNode valid = card();
            valid.put(entry.getKey(), "x".repeat(entry.getValue()));
            mvc.perform(post("/api/projects/{id}/board/cards", project)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + entry.getKey()).value("x".repeat(entry.getValue())));
            ObjectNode overflow = card();
            overflow.put(entry.getKey(), "x".repeat(entry.getValue() + 1));
            invalidPost(project, overflow, entry.getKey());
        }
        assertThat(cardCount(project)).isEqualTo(4);
    }

    @Test
    void calendarDateRequiresExactIsoDayAndAcceptsLeapDay() throws Exception {
        String project = project();
        ObjectNode leap = card();
        leap.put("dueDate", "2024-02-29");
        mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(leap)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dueDate").value("2024-02-29"));
        for (String bad : List.of("2025-02-29", "2026-04-31", "2026-2-01", "2026-12-1", "not-a-date", " 2026-12-01")) {
            ObjectNode body = card();
            body.put("dueDate", bad);
            invalidPost(project, body, "dueDate");
        }
        assertThat(cardCount(project)).isEqualTo(1);
    }

    @Test
    void invalidPriorityModelColumnAndNegativeEstimateAreValidationErrors() throws Exception {
        String project = project();
        ObjectNode priority = card();
        priority.put("priority", "urgent");
        invalidPost(project, priority, "priority");
        ObjectNode column = card();
        column.put("columnKey", "missing-column");
        invalidPost(project, column, "columnKey");
        ObjectNode estimate = card();
        estimate.put("estimate", -1);
        invalidPost(project, estimate, "estimate");
        mvc.perform(post("/api/projects/{id}/board/cards", project).param("model", "unknown")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors.model").exists());
        mvc.perform(get("/api/projects/{id}/board", project).param("model", "unknown"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.model").exists());
        assertThat(cardCount(project)).isZero();
    }

    @Test
    void missingPrimitiveFieldsKeepLegacyZeroAndFalseAndMalformedJsonIsBadRequest() throws Exception {
        String project = project();
        ObjectNode body = card();
        body.remove("estimate");
        body.remove("blocked");
        mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estimate").value(0))
            .andExpect(jsonPath("$.blocked").value(false));
        ObjectNode legacyEstimate = card();
        legacyEstimate.put("estimate", 101);
        mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(legacyEstimate)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estimate").value(101));
        mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(cardCount(project)).isEqualTo(2);
    }

    @Test
    void missingProjectCardAndForeignCardReturnStructuredNotFoundWithoutMutation() throws Exception {
        String first = project();
        String second = project();
        String cardId = createdCard(first);
        List<Map<String, Object>> before = jdbc.queryForList("select * from board_cards where id=?", cardId);
        mvc.perform(post("/api/projects/missing/board/cards")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(get("/api/projects/missing/board"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(put("/api/projects/missing/board/cards/{card}", cardId)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(delete("/api/projects/missing/board/cards/{card}", cardId))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(put("/api/projects/{project}/board/cards/missing", first)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_CARD_NOT_FOUND"));
        mvc.perform(delete("/api/projects/{project}/board/cards/missing", first))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_CARD_NOT_FOUND"));
        mvc.perform(put("/api/projects/{project}/board/cards/{card}", second, cardId)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_CARD_NOT_FOUND"));
        mvc.perform(delete("/api/projects/{project}/board/cards/{card}", second, cardId))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_CARD_NOT_FOUND"));
        assertThat(jdbc.queryForList("select * from board_cards where id=?", cardId)).isEqualTo(before);
    }

    @Test
    void invalidPutRollsBackAndSuccessfulLifecycleKeepsExistingStatuses() throws Exception {
        String project = project();
        String id = createdCard(project);
        List<Map<String, Object>> before = jdbc.queryForList("select * from board_cards where id=?", id);
        ObjectNode invalid = card();
        invalid.put("columnKey", "ready");
        invalid.put("title", "changed");
        invalid.put("dueDate", "2026-02-30");
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(invalid)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.dueDate").exists());
        assertThat(jdbc.queryForList("select * from board_cards where id=?", id)).isEqualTo(before);
        invalid.put("dueDate", "2026-12-31");
        invalid.put("priority", "urgent");
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(invalid)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.priority").exists());
        assertThat(jdbc.queryForList("select * from board_cards where id=?", id)).isEqualTo(before);
        ObjectNode valid = card();
        valid.put("columnKey", "ready");
        valid.put("title", "changed");
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(valid)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.title").value("changed"));
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, id))
            .andExpect(status().isNoContent());
        assertThat(cardCount(project)).isZero();
    }

    private void invalidPost(String project, ObjectNode body, String field) throws Exception {
        mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors." + field).exists());
    }

    private String project() throws Exception {
        String body = """
            {"name":"Validation project","owner":"Owner","status":"active","health":"green",
             "deliveryModel":"kanban","quarter":"Q4","deadline":"2026-12-31","milestone":"Gate",
             "risk":"Risk","dependency":"Dependency","kpiName":"KPI","kpiTarget":"Target","summary":"Review"}
            """;
        MvcResult result = mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private ObjectNode card() {
        ObjectNode body = json.createObjectNode();
        body.put("title", "Task");
        body.put("description", "Description");
        body.put("owner", "Owner");
        body.put("dueDate", "2026-12-31");
        body.put("columnKey", "backlog");
        body.put("priority", "medium");
        body.put("labels", "review");
        body.put("estimate", 1);
        body.put("blocked", false);
        return body;
    }

    private String createdCard(String project) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects/{project}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card())))
            .andExpect(status().isOk()).andReturn();
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        return response.path("id").asText();
    }

    private long cardCount(String project) {
        return jdbc.queryForObject("select count(*) from board_cards where project_id=?", Long.class, project);
    }
}
