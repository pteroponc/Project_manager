package project_manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_board_models;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class BoardModelsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String project() throws Exception {
        String body = """
            {"name":"Board models review","owner":"Review","status":"active","health":"green",
             "deliveryModel":"kanban","quarter":"Q4","deadline":"2026-12-31","milestone":"Gate",
             "risk":"Risk","dependency":"Dependency","kpiName":"KPI","kpiTarget":"Target","summary":"Review"}
            """;
        var result = mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private Map<String, Object> card(String column) {
        return Map.of("title", "Shared task", "description", "One server task in all models", "owner", "Review",
            "dueDate", "2026-12-20", "columnKey", column, "priority", "medium", "labels", "review", "estimate", 3, "blocked", false);
    }

    @Test
    void modelReadsPreserveAllStoredFieldsAndExplicitMoveReusesTheSameTask() throws Exception {
        String project = project();
        var result = mvc.perform(post("/api/projects/{id}/board/cards", project).param("model", "kanban")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("backlog"))))
            .andExpect(status().isOk()).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).path("id").asText();
        var before = jdbc.queryForList("select * from board_cards where project_id=?", project);
        for (String model : new String[]{"kanban", "scrum", "waterfall"}) {
            mvc.perform(get("/api/projects/{id}/board", project).param("model", model))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryModel").value(model))
                .andExpect(jsonPath(model.equals("kanban") ? "$.columns[*].cards[*].id" : "$.unassignedColumns[*].cards[*].id").value(hasItem(id)));
            assertThat(jdbc.queryForList("select * from board_cards where project_id=?", project)).isEqualTo(before);
        }
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id).param("model", "scrum")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("in-sprint"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("select column_key from board_cards where id=?", String.class, id)).isEqualTo("in-sprint");
        assertThat(jdbc.queryForObject("select count(*) from board_cards where project_id=?", Integer.class, project)).isEqualTo(1);
        mvc.perform(get("/api/projects/{id}/board", project).param("model", "kanban"))
            .andExpect(jsonPath("$.unassignedColumns[*].cards[*].id").value(hasItem(id)));
    }

    @Test
    void invalidDestinationDoesNotChangeTaskOrFallbackToAnotherColumn() throws Exception {
        String project = project();
        var result = mvc.perform(post("/api/projects/{id}/board/cards", project).param("model", "kanban")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("backlog"))))
            .andExpect(status().isOk()).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).path("id").asText();
        var before = jdbc.queryForList("select * from board_cards where id=?", id);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id).param("model", "scrum")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("unknown-column"))))
            .andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("select * from board_cards where id=?", id)).isEqualTo(before);
    }
}
