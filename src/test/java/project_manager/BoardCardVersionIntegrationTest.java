package project_manager;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_card_version;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BoardCardVersionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createReadUpdateNoOpAndStaleWritesUseCardVersionWithoutPartialChange() throws Exception {
        String project = project();
        String id = create(project);
        mvc.perform(get("/api/projects/{id}/board", project))
            .andExpect(status().isOk()).andExpect(jsonPath("$.columns[0].cards[0].version").value(0));
        ObjectNode changed = card("Changed", 0);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(changed)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        ObjectNode same = card("Changed", 1);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(same)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        List<Map<String, Object>> before = row(id);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("Stale", 0))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOARD_CARD_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(2));
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, id).param("expectedVersion", "1"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOARD_CARD_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(2));
        assertThat(row(id)).isEqualTo(before);
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, id).param("expectedVersion", "2"))
            .andExpect(status().isNoContent());
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("Late", 2))))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_CARD_NOT_FOUND"));
    }

    @Test
    void missingMalformedAndNegativeTokensAre400AndForeignOrDeletedCardsRemain404() throws Exception {
        String first = project();
        String second = project();
        String id = create(first);
        List<Map<String, Object>> before = row(id);
        for (String bad : List.of("missing", "negative")) {
            ObjectNode body = card("Attempt", 0);
            if (bad.equals("missing")) body.remove("expectedVersion");
            else body.put("expectedVersion", -1);
            mvc.perform(put("/api/projects/{project}/board/cards/{id}", first, id)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());
        }
        for (String token : List.of("", "-1", "abc", "999999999999999999999999")) {
            mvc.perform(delete("/api/projects/{project}/board/cards/{id}", first, id)
                    .param("expectedVersion", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());
        }
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", first, id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", second, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("Foreign", 0))))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_CARD_NOT_FOUND"));
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", second, id).param("expectedVersion", "0"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_CARD_NOT_FOUND"));
        assertThat(row(id)).isEqualTo(before);
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", first, id).param("expectedVersion", "0"))
            .andExpect(status().isNoContent());
        String replacement = create(first);
        assertThat(replacement).isNotEqualTo(id);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", first, id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("Old ID", 0))))
            .andExpect(status().isNotFound());
        assertThat(row(replacement)).hasSize(1);
    }

    @Test
    void simultaneousPutsFromSameVersionCommitExactlyOneChange() throws Exception {
        String project = project();
        String id = create(project);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentPut(project, id, "First", ready, start));
            var second = executor.submit(() -> concurrentPut(project, id, "Second", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            var row = row(id).getFirst();
            assertThat(row.get("VERSION")).isEqualTo(1L);
            assertThat(row.get("TITLE")).isIn("First", "Second");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void moveAndNetworkReplayAdvanceOnlyTheMovedCardOnce() throws Exception {
        String project = project();
        String moved = create(project);
        String neighbor = create(project);
        List<Map<String, Object>> neighborBefore = row(neighbor);
        ObjectNode request = card("Moved", 0);
        request.put("columnKey", "ready");
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, moved)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(row(moved).getFirst()).containsEntry("COLUMN_KEY", "ready")
            .containsEntry("POSITION", 0).containsEntry("VERSION", 1L);
        List<Map<String, Object>> afterMove = row(moved);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, moved)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOARD_CARD_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(1));
        ObjectNode staleDifferentColumn = card("Stale move", 0);
        staleDifferentColumn.put("columnKey", "in-progress");
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, moved)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(staleDifferentColumn)))
            .andExpect(status().isConflict());
        assertThat(row(moved)).isEqualTo(afterMove);
        assertThat(row(neighbor)).isEqualTo(neighborBefore);
        mvc.perform(get("/api/projects/{id}/board", project))
            .andExpect(status().isOk()).andExpect(jsonPath("$.columns[1].cards[0].version").value(1))
            .andExpect(jsonPath("$.storedPositions['" + moved + "']").value(0));
    }

    @Test
    void simultaneousMovesToDifferentColumnsHaveOneWinnerAndOneConflict() throws Exception {
        String project = project();
        String id = create(project);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentPut(project, id, "Ready", "ready", ready, start));
            var second = executor.submit(() -> concurrentPut(project, id, "Done", "done", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
            var winner = row(id).getFirst();
            assertThat(winner.get("VERSION")).isEqualTo(1L);
            assertThat(winner.get("POSITION")).isEqualTo(0);
            assertThat(winner.get("COLUMN_KEY")).isIn("ready", "done");
            assertThat(winner.get("TITLE")).isEqualTo(
                winner.get("COLUMN_KEY").equals("ready") ? "Ready" : "Done");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentEditAndDeleteCannotLeaveAStalePartialCard() throws Exception {
        String project = project();
        String id = create(project);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var edit = executor.submit(() -> concurrentPut(project, id, "Edited", "ready", ready, start));
            var delete = executor.submit(() -> {
                ready.countDown();
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                    return mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, id)
                            .param("expectedVersion", "0")).andReturn().getResponse().getStatus();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int editStatus = edit.get(10, TimeUnit.SECONDS);
            int deleteStatus = delete.get(10, TimeUnit.SECONDS);
            assertThat(List.of(editStatus, deleteStatus)).isIn(List.of(200, 409), List.of(404, 204));
            if (editStatus == 200) {
                assertThat(row(id).getFirst()).containsEntry("TITLE", "Edited")
                    .containsEntry("COLUMN_KEY", "ready").containsEntry("VERSION", 1L);
            } else {
                assertThat(row(id)).isEmpty();
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private int concurrentPut(String project, String id, String title, CountDownLatch ready,
                              CountDownLatch start) {
        return concurrentPut(project, id, title, "backlog", ready, start);
    }

    private int concurrentPut(String project, String id, String title, String columnKey,
                              CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
            ObjectNode request = card(title, 0);
            request.put("columnKey", columnKey);
            return mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, id)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String project() throws Exception {
        String body = """
            {"name":"Version project","owner":"Owner","status":"active","health":"green",
             "deliveryModel":"kanban","quarter":"Q4","deadline":"2026-12-31","milestone":"Gate",
             "risk":"Risk","dependency":"Dependency","kpiName":"KPI","kpiTarget":"Target","summary":"Review"}
            """;
        MvcResult result = mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private String create(String project) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card("Original", null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private ObjectNode card(String title, Integer version) {
        ObjectNode body = json.createObjectNode();
        body.put("title", title);
        body.put("description", "Description");
        body.put("owner", "Owner");
        body.put("dueDate", "2026-12-31");
        body.put("columnKey", "backlog");
        body.put("priority", "medium");
        body.put("labels", "review");
        body.put("estimate", 1);
        body.put("blocked", false);
        if (version != null) body.put("expectedVersion", version);
        return body;
    }

    private List<Map<String, Object>> row(String id) {
        return jdbc.queryForList("select * from board_cards where id=?", id);
    }
}
