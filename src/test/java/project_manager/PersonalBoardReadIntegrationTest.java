package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_personal_board_read;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class PersonalBoardReadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void combinesIndependentAndProjectCardsWithoutChangingStoredStatesOrPositions() throws Exception {
        String project = createProject();
        entityManager.flush();
        insert("independent", null, "backlog", 7, "Independent", "", 3);
        insert("project-card", project, "backlog", 2, "Project card", "2026-12-20", 1);
        insert("legacy", project, "release", 4, "Legacy card", "2026-12-21", 0);
        List<Map<String, Object>> before = jdbc.queryForList("select * from board_cards order by id");

        String body = mvc.perform(get("/api/board/personal"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(body);
        assertThat(result.path("columns").size()).isEqualTo(4);
        assertThat(result.path("columns").get(0).path("key").asText()).isEqualTo("backlog");
        JsonNode cards = result.path("columns").get(0).path("tasks");
        assertThat(cards.size()).isEqualTo(2);
        assertThat(cards.get(0).path("id").asText()).isEqualTo("independent");
        assertThat(cards.get(0).path("projectId").isNull()).isTrue();
        assertThat(cards.get(0).path("dueDate").asText()).isEmpty();
        assertThat(cards.get(0).path("version").asLong()).isEqualTo(3);
        assertThat(cards.get(1).path("id").asText()).isEqualTo("project-card");
        assertThat(cards.get(1).path("projectName").asText()).isEqualTo("Personal board project");
        assertThat(result.path("legacyTasks").get(0).path("columnKey").asText()).isEqualTo("release");
        assertThat(jdbc.queryForList("select * from board_cards order by id")).isEqualTo(before);
    }

    private String createProject() throws Exception {
        String body = """
            {"name":"Personal board project","owner":"Review","status":"active","health":"green",
             "deliveryModel":"waterfall","quarter":"Q4","deadline":"2026-12-31","milestone":"Gate",
             "risk":"Risk","dependency":"Dependency","kpiName":"KPI","kpiTarget":"Target","summary":"Review"}
            """;
        String response = mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asText();
    }

    private void insert(String id, String project, String state, int position, String title, String due, long version) {
        jdbc.update("""
            insert into board_cards
            (id, project_id, column_key, position, title, description, owner, due_date,
             priority, labels, estimate, blocked, version)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, project, state, position, title, "Description", "Review", due,
            "medium", "", 0, false, version);
    }
}
