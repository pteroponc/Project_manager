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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_personal_task_mutation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PersonalTaskMutationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void linksUnlinksAndTransfersSameCardWithHistoryAndNoOp() throws Exception {
        String a = project();
        String b = project("scrum");
        String id = create(null, "ready", "2026-12-20");
        create(a, "ready", null);
        List<String> targets = java.util.Arrays.asList(a, null, b, a);
        for (int i = 0; i < targets.size(); i++) {
            relink(id, targets.get(i), i).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.version").value(i + 1))
                .andExpect(jsonPath("$.title").value("Task")).andExpect(jsonPath("$.owner").value(""));
            JsonNode entry = change(id, i + 1);
            assertThat(entry.path("OPERATION").asText()).isEqualTo("UPDATE");
            assertThat(entry.path("BEFORE_VERSION").asInt()).isEqualTo(i);
            assertThat(entry.path("AFTER_VERSION").asInt()).isEqualTo(i + 1);
            JsonNode before = json.readTree(entry.path("BEFORE_JSON").asText());
            JsonNode after = json.readTree(entry.path("AFTER_JSON").asText());
            assertThat(before.get("projectId").asText(null)).isEqualTo(i == 0 ? null : targets.get(i - 1));
            assertThat(after.get("projectId").asText(null)).isEqualTo(targets.get(i));
            assertThat(after.size()).isEqualTo(i == 2 ? 1 : 2);
            if (i != 2) assertThat(after.get("position").asInt()).isEqualTo(i == 1 ? 0 : 1);
        }
        relink(id, a, 4).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(4));
        relink(id, "missing", 4).andExpect(status().isNotFound());
        relink(id, b, 3).andExpect(status().isConflict());
        assertThat(countChanges(id)).isEqualTo(5);
        assertThat(jdbc.queryForList("select project_id from board_cards where id=?", String.class, id))
            .containsExactly(a);
        assertThat(personalBoard().toString()).contains(id);
    }

    @Test
    void concurrentTransfersSerializeInBothDirectionsAndRejectStaleVersion() throws Exception {
        String a = project();
        String b = project("waterfall");
        String left = create(a, "ready", "2026-12-20");
        String right = create(b, "ready", "2026-12-20");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return relink(left, b, 0).andReturn().getResponse().getStatus(); });
            var second = executor.submit(() -> { start.await(); return relink(right, a, 0).andReturn().getResponse().getStatus(); });
            var stale = executor.submit(() -> { start.await(); return relink(left, b, 0).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), stale.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(countChanges(left)).isEqualTo(2);
        assertThat(countChanges(right)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, left)).isEqualTo(b);
        assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, right)).isEqualTo(a);
    }

    @Test
    void personalDeleteKeepsHistoryAndProjectForBothCardOrigins() throws Exception {
        String project = project();
        for (String origin : java.util.Arrays.asList(null, project)) {
            String id = create(origin, "backlog", "2026-12-20");
            update(id, 0, "Edited", "Description", "2026-12-20", "medium", "ready").andExpect(status().isOk());
            mvc.perform(delete("/api/board/personal/tasks/{id}", id)).andExpect(status().isBadRequest());
            deletePersonal(id, "0").andExpect(status().isConflict());
            deletePersonal(id, "1").andExpect(status().isNoContent());
            deletePersonal(id, "1").andExpect(status().isNotFound());
            assertThat(jdbc.queryForList("select id from board_cards where id=?", String.class, id)).isEmpty();
            JsonNode entry = change(id, 2);
            assertThat(countChanges(id)).isEqualTo(3);
            assertThat(entry.path("OPERATION").asText()).isEqualTo("DELETE");
            assertThat(entry.path("BEFORE_VERSION").asInt()).isEqualTo(1);
            assertThat(entry.path("AFTER_VERSION").asInt()).isEqualTo(1);
            assertThat(entry.path("CONFIRMATION").isNull()).isTrue();
            assertThat(json.readTree(entry.path("BEFORE_JSON").asText()).path("title").asText()).isEqualTo("Edited");
            assertThat(entry.path("AFTER_JSON").asText()).isEqualTo("{}");
        }
        assertThat(jdbc.queryForObject("select count(*) from projects where id=?", Integer.class, project)).isEqualTo(1);
    }

    @Test
    void incompatibleReferenceBlocksTransferAndDeleteWithoutChangingEitherRow() throws Exception {
        String a = project();
        String b = project();
        String id = create(a, "ready", "2026-12-20");
        jdbc.execute("ALTER TABLE BOARD_CARDS ADD CONSTRAINT TEST_CARD_PROJECT UNIQUE (ID, PROJECT_ID)");
        try {
            jdbc.execute("CREATE TABLE TEST_CARD_DEPENDENCY (CARD_ID VARCHAR(255), PROJECT_ID VARCHAR(255), "
                + "FOREIGN KEY (CARD_ID, PROJECT_ID) REFERENCES BOARD_CARDS(ID, PROJECT_ID))");
            jdbc.update("INSERT INTO TEST_CARD_DEPENDENCY VALUES (?,?)", id, a);
            relink(id, b, 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOARD_CARD_DEPENDENCY_CONFLICT"));
            deletePersonal(id, "0").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOARD_CARD_DEPENDENCY_CONFLICT"));
            assertThat(countChanges(id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, id)).isEqualTo(a);
            assertThat(jdbc.queryForObject("select project_id from TEST_CARD_DEPENDENCY where card_id=?", String.class, id)).isEqualTo(a);
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS TEST_CARD_DEPENDENCY");
            jdbc.execute("ALTER TABLE BOARD_CARDS DROP CONSTRAINT TEST_CARD_PROJECT");
        }
    }

    @Test
    void historyFailureRollsBackRelinkAndDelete() throws Exception {
        String project = project();
        String id = create(null, "ready", "2026-12-20");
        jdbc.execute("ALTER TABLE TASK_CHANGE_ENTRIES ADD CONSTRAINT TEST_ONLY_CREATE CHECK (TASK_ID <> '"
            + id + "' OR OPERATION = 'CREATE')");
        try {
            relink(id, project, 0).andExpect(status().isInternalServerError());
            deletePersonal(id, "0").andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForMap("select project_id, version from board_cards where id=?", id))
                .containsEntry("PROJECT_ID", null).containsEntry("VERSION", 0L);
            assertThat(countChanges(id)).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE TASK_CHANGE_ENTRIES DROP CONSTRAINT TEST_ONLY_CREATE");
        }
    }

    private org.springframework.test.web.servlet.ResultActions relink(String id, String target, long version) throws Exception {
        return mvc.perform(put("/api/board/personal/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
            .content(body(target, version, "Task", "Description", "2026-12-20", "medium", "ready")));
    }

    private org.springframework.test.web.servlet.ResultActions deletePersonal(String id, String version) throws Exception {
        return mvc.perform(delete("/api/board/personal/tasks/{id}", id).param("expectedVersion", version));
    }

    @Test
    void movingIntoOccupiedProjectColumnAppendsAndKeepsProjectReorderUsable() throws Exception {
        String project = project();
        String a = create(project, "backlog", null);
        String b = create(project, "ready", null);
        assertThat(position(a)).isZero();
        assertThat(position(b)).isZero();

        update(a, 0, "Task", "Description", null, "medium", "ready")
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(position(b)).isZero();
        assertThat(position(a)).isEqualTo(1);
        JsonNode move = change(a, 1);
        assertThat(move.path("OPERATION").asText()).isEqualTo("MOVE");
        JsonNode before = json.readTree(move.path("BEFORE_JSON").asText());
        assertThat(before.path("columnKey").asText()).isEqualTo("backlog");
        assertThat(before.path("position").asInt()).isZero();
        JsonNode after = json.readTree(move.path("AFTER_JSON").asText());
        assertThat(after.path("columnKey").asText()).isEqualTo("ready");
        assertThat(after.path("position").asInt()).isEqualTo(1);

        JsonNode order = projectOrder(project, "ready");
        assertThat(order.path("cards").size()).isEqualTo(2);
        assertThat(order.path("cards").get(0).path("id").asText()).isEqualTo(b);
        assertThat(order.path("cards").get(1).path("id").asText()).isEqualTo(a);
        assertThat(Set.of(order.path("cards").get(0).path("id").asText(),
            order.path("cards").get(1).path("id").asText())).containsExactlyInAnyOrder(a, b);

        var reorder = json.createObjectNode();
        reorder.put("direction", "up");
        reorder.set("expectedOrder", order.path("cards").deepCopy());
        mvc.perform(post("/api/projects/{project}/board/kanban/columns/ready/cards/{id}/reorder", project, a)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(reorder)))
            .andExpect(status().isOk());
        assertThat(position(a)).isZero();
        assertThat(position(b)).isEqualTo(1);
        update(a, 1, "Stale", "Description", null, "medium", "done")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOARD_CARD_VERSION_CONFLICT"));
        assertThat(countChanges(a)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from board_cards where id in (?,?)", Integer.class, a, b))
            .isEqualTo(2);
    }

    @Test
    void linkedScrumAndWaterfallTasksRemainVisibleWithoutChangingHistoricalStates() throws Exception {
        for (String model : List.of("scrum", "waterfall")) {
            String project = project(model);
            String nativeState = model.equals("scrum") ? "product-backlog" : "initiation";
            String historical = projectCard(project, nativeState);
            String linked = create(project, "backlog", null);
            JsonNode board = projectBoard(project);
            assertThat(board.path("columns").toString()).contains(historical);
            assertThat(board.path("unassignedColumns").toString()).contains(linked);
            assertThat(board.path("unassignedColumns").toString()).doesNotContain(historical);
            assertThat(personalBoard().path("legacyTasks").toString()).contains(historical);
            assertThat(personalBoard().path("columns").toString()).contains(linked);

            update(linked, 0, "Still visible", "Description", null, "medium", "ready")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
            assertThat(projectBoard(project).path("unassignedColumns").toString()).contains(linked);
            assertThat(personalBoard().path("columns").toString()).contains(linked);
            update(linked, 1, "Still visible", "Description", null, "medium", "in-progress")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
            assertThat(projectBoard(project).path("unassignedColumns").toString()).contains(linked);
            update(linked, 2, "Still visible", "Description", null, "medium", "done")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
            assertThat(projectBoard(project).path("columns").toString()).contains(linked);
            assertThat(projectBoard(project).path("unassignedColumns").toString()).doesNotContain(linked);
            assertThat(jdbc.queryForObject("select column_key from board_cards where id=?", String.class, historical))
                .isEqualTo(nativeState);
            assertThat(countChanges(linked)).isEqualTo(4);
            assertThat(change(linked, 1).path("AFTER_VERSION").asLong()).isEqualTo(1);
            assertThat(change(linked, 2).path("AFTER_VERSION").asLong()).isEqualTo(2);
            assertThat(change(linked, 3).path("AFTER_VERSION").asLong()).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from board_cards where id in (?,?)", Integer.class,
                historical, linked)).isEqualTo(2);
        }
    }

    @Test
    void independentTaskLifecycleKeepsOneRowAndOrderedAtomicHistory() throws Exception {
        String id = create(null, "backlog", null);
        assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select due_date from board_cards where id=?", String.class, id)).isEmpty();
        JsonNode creation = change(id, 0);
        assertThat(creation.path("OPERATION").asText()).isEqualTo("CREATE");
        assertThat(creation.path("BEFORE_VERSION").isNull()).isTrue();
        assertThat(creation.path("AFTER_VERSION").asLong()).isZero();
        assertThat(creation.path("SOURCE").asText()).isEqualTo("PERSONAL_BOARD_HTTP");
        assertThat(creation.path("INPUT_METHOD").asText()).isEqualTo("HTTP_REQUEST");
        assertThat(creation.path("CONFIRMATION").isNull()).isTrue();
        assertThat(creation.path("CHANGED_AT").asText()).isNotBlank();
        assertThat(json.readTree(creation.path("AFTER_JSON").asText()).path("dueDate").isNull()).isTrue();

        update(id, 0, "Revised", "Changed", "2026-12-20", "high", "ready")
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        JsonNode first = change(id, 1);
        assertThat(first.path("OPERATION").asText()).isEqualTo("MOVE");
        assertThat(first.path("BEFORE_VERSION").asLong()).isZero();
        assertThat(first.path("AFTER_VERSION").asLong()).isEqualTo(1);
        JsonNode before = json.readTree(first.path("BEFORE_JSON").asText());
        JsonNode after = json.readTree(first.path("AFTER_JSON").asText());
        assertThat(before.path("title").asText()).isEqualTo("Task");
        assertThat(after.path("title").asText()).isEqualTo("Revised");
        assertThat(before.path("dueDate").isNull()).isTrue();
        assertThat(after.path("dueDate").asText()).isEqualTo("2026-12-20");
        assertThat(after.path("priority").asText()).isEqualTo("high");

        update(id, 1, "Revised", "Changed", null, "high", "done")
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        JsonNode completion = change(id, 2);
        assertThat(completion.path("OPERATION").asText()).isEqualTo("COMPLETE");
        assertThat(json.readTree(completion.path("AFTER_JSON").asText()).path("dueDate").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from board_cards where id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select column_key from board_cards where id=?", String.class, id)).isEqualTo("done");
        mvc.perform(get("/api/board/personal")).andExpect(status().isOk())
            .andExpect(jsonPath("$.columns[3].tasks[?(@.id=='" + id + "')].title").value("Revised"));

        update(id, 2, "Revised", "Changed", null, "high", "done")
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(countChanges(id)).isEqualTo(3);
        assertThat(jdbc.queryForList("select sequence from task_change_entries where task_id=? order by sequence", Long.class, id))
            .isSorted();
    }

    @Test
    void projectTaskUsesSameHistoryAndOldDeleteStillWorks() throws Exception {
        String project = project();
        String id = create(project, "backlog", null);
        assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, id)).isEqualTo(project);
        assertThat(jdbc.queryForObject("select count(*) from projects where id=?", Integer.class, project)).isEqualTo(1);
        update(id, 0, "Project task", "Description", null, "medium", "ready")
            .andExpect(status().isOk());
        assertThat(countChanges(id)).isEqualTo(2);

        String old = """
            {"title":"Old route","description":"Description","owner":"Review","dueDate":"2026-12-20",
             "priority":"medium","columnKey":"backlog","labels":"","estimate":0,"blocked":false,"expectedVersion":0}
            """;
        String oldId = json.readTree(mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(old))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        assertThat(change(oldId, 0).path("INPUT_METHOD").asText()).isEqualTo("HTTP_REQUEST");
        String oldUpdate = old.replace("Old route", "Old route edited");
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, oldId)
                .contentType(MediaType.APPLICATION_JSON).content(oldUpdate))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(change(oldId, 1).path("OPERATION").asText()).isEqualTo("UPDATE");
        assertThat(change(oldId, 1).path("AFTER_VERSION").asLong()).isEqualTo(1);
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, oldId)
                .param("expectedVersion", "1"))
            .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from board_cards where id=?", Integer.class, oldId)).isZero();
        assertThat(countChanges(oldId)).isEqualTo(2);
    }

    @Test
    void validationAndVersionConflictDoNotWriteHistoryOrOverwriteTask() throws Exception {
        String id = create(null, "backlog", null);
        update(id, 0, "Fresh", "Description", null, "medium", "backlog").andExpect(status().isOk());
        List<Map<String, Object>> before = jdbc.queryForList("select * from board_cards where id=?", id);
        update(id, 0, "Stale", "Description", null, "medium", "ready")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOARD_CARD_VERSION_CONFLICT"));
        update(id, 1, "Invalid", "Description", "2026-02-30", "medium", "ready")
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForList("select * from board_cards where id=?", id)).isEqualTo(before);
        assertThat(countChanges(id)).isEqualTo(2);
        mvc.perform(post("/api/board/personal/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(body("missing-project", 0, "Orphan", "Description", null, "medium", "ready")))
            .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from board_cards where project_id='missing-project'", Integer.class))
            .isZero();
    }

    @Test
    void editingLegacyBlankPriorityDoesNotAssignOne() throws Exception {
        String id = create(null, "backlog", null);
        jdbc.update("update board_cards set priority='' where id=?", id);
        mvc.perform(put("/api/board/personal/tasks/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(null, 0, "Edited title", "Description", null, null, "ready")))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select priority from board_cards where id=?", String.class, id)).isEmpty();
        JsonNode change = change(id, 1);
        assertThat(json.readTree(change.path("AFTER_JSON").asText()).has("priority")).isFalse();
    }

    @Test
    void failedHistoryInsertRollsBackCardMutation() throws Exception {
        String id = create(null, "backlog", null);
        List<Map<String, Object>> before = jdbc.queryForList("select * from board_cards where id=?", id);
        int historyBefore = countChanges(id);
        jdbc.execute("ALTER TABLE TASK_CHANGE_ENTRIES ADD CONSTRAINT REJECT_UPDATE_FOR_TEST CHECK (AFTER_JSON NOT LIKE '%Cannot persist%')");
        try {
            update(id, 0, "Cannot persist", "Description", null, "medium", "backlog")
                .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForList("select * from board_cards where id=?", id)).isEqualTo(before);
            assertThat(countChanges(id)).isEqualTo(historyBefore);
        } finally {
            jdbc.execute("ALTER TABLE TASK_CHANGE_ENTRIES DROP CONSTRAINT REJECT_UPDATE_FOR_TEST");
        }
    }

    private String create(String projectId, String state, String due) throws Exception {
        String response = mvc.perform(post("/api/board/personal/tasks")
                .header("X-Task-Form-Submission", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(projectId, 0, "Task", "Description", due, "medium", state)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions update(String id, long version, String title,
            String description, String due, String priority, String state) throws Exception {
        String project = jdbc.queryForObject("select project_id from board_cards where id=?", String.class, id);
        return mvc.perform(put("/api/board/personal/tasks/{id}", id)
            .header("X-Task-Form-Submission", "true")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(project, version, title, description, due, priority, state)));
    }

    private String body(String project, long version, String title, String description, String due,
                        String priority, String state) throws Exception {
        var body = json.createObjectNode();
        if (project == null) body.putNull("projectId"); else body.put("projectId", project);
        body.put("expectedVersion", version);
        body.put("title", title);
        body.put("description", description);
        if (due == null) body.putNull("dueDate"); else body.put("dueDate", due);
        body.put("priority", priority);
        body.put("columnKey", state);
        return json.writeValueAsString(body);
    }

    private int countChanges(String id) {
        return jdbc.queryForObject("select count(*) from task_change_entries where task_id=?", Integer.class, id);
    }

    private int position(String id) {
        return jdbc.queryForObject("select position from board_cards where id=?", Integer.class, id);
    }

    private JsonNode projectOrder(String project, String column) throws Exception {
        return json.readTree(mvc.perform(get("/api/projects/{project}/board/kanban/columns/{column}/order", project, column))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode projectBoard(String project) throws Exception {
        return json.readTree(mvc.perform(get("/api/projects/{project}/board", project))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode personalBoard() throws Exception {
        return json.readTree(mvc.perform(get("/api/board/personal"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String projectCard(String project, String state) throws Exception {
        String body = """
            {"title":"Historical","description":"Description","owner":"Review","dueDate":"2026-12-20",
             "priority":"medium","columnKey":"STATE","labels":"","estimate":0,"blocked":false,"expectedVersion":0}
            """.replace("STATE", state);
        String response = mvc.perform(post("/api/projects/{project}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asText();
    }

    private JsonNode change(String id, int index) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from task_change_entries where task_id=? order by sequence", id);
        return json.valueToTree(rows.get(index));
    }

    private String project() throws Exception {
        return project("kanban");
    }

    private String project(String model) throws Exception {
        String body = """
            {"name":"Task history project","owner":"Review","status":"active","health":"green",
             "deliveryModel":"kanban","quarter":"Q4","deadline":"2026-12-31","milestone":"Gate",
             "risk":"Risk","dependency":"Dependency","kpiName":"KPI","kpiTarget":"Target","summary":"Review"}
            """.replace("\"deliveryModel\":\"kanban\"", "\"deliveryModel\":\"" + model + "\"");
        String response = mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asText();
    }
}
