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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_board_order;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BoardOrderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void emptyColumnAndNormalUpDownBoundaryAndSingleCard() throws Exception {
        String project = project();
        assertThat(order(project, "ready").path("cards")).isEmpty();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        String c = card(project, "ready", "C");
        JsonNode initial = order(project, "ready");
        assertThat(ids(initial)).containsExactly(a, b, c);
        List<Map<String, Object>> beforeNoOp = rows(project);
        JsonNode unchanged = reorder(project, "ready", a, "up", initial, 200);
        assertThat(unchanged.path("changed").asBoolean()).isFalse();
        assertThat(rows(project)).isEqualTo(beforeNoOp);

        JsonNode up = reorder(project, "ready", b, "up", initial, 200);
        assertThat(up.path("changed").asBoolean()).isTrue();
        assertThat(ids(up)).containsExactly(b, a, c);
        assertRow(b, "ready", 0, 1);
        assertRow(a, "ready", 1, 1);
        assertRow(c, "ready", 2, 0);

        JsonNode down = reorder(project, "ready", b, "down", order(project, "ready"), 200);
        assertThat(ids(down)).containsExactly(a, b, c);
        assertRow(a, "ready", 0, 2);
        assertRow(b, "ready", 1, 2);
        assertRow(c, "ready", 2, 0);
        List<Map<String, Object>> beforeLast = rows(project);
        reorder(project, "ready", c, "down", order(project, "ready"), 200);
        assertThat(rows(project)).isEqualTo(beforeLast);

        String only = card(project, "review", "Only");
        List<Map<String, Object>> beforeSingle = rows(project);
        JsonNode single = reorder(project, "review", only, "down", order(project, "review"), 200);
        assertThat(single.path("changed").asBoolean()).isFalse();
        assertThat(rows(project)).isEqualTo(beforeSingle);
    }

    @Test
    void gapsDuplicatesAndNegativePositionsNormalizeOnlyAfterRealMoveAndBumpExactlyOnce() throws Exception {
        String project = project();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        String c = card(project, "ready", "C");
        jdbc.update("update board_cards set position=0 where id=?", a);
        jdbc.update("update board_cards set position=4 where id=?", b);
        jdbc.update("update board_cards set position=9 where id=?", c);
        JsonNode gaps = order(project, "ready");
        JsonNode compacted = reorder(project, "ready", b, "up", gaps, 200);
        assertThat(ids(compacted)).containsExactly(b, a, c);
        assertRow(b, "ready", 0, 1);
        assertRow(a, "ready", 1, 1);
        assertRow(c, "ready", 2, 1);

        jdbc.update("update board_cards set position=0 where id=?", a);
        jdbc.update("update board_cards set position=0 where id=?", b);
        jdbc.update("update board_cards set position=4 where id=?", c);
        JsonNode duplicate = order(project, "ready");
        String first = ids(duplicate).get(0), second = ids(duplicate).get(1);
        assertThat(duplicate.path("cards").get(0).path("position").asInt()).isZero();
        assertThat(duplicate.path("cards").get(1).path("position").asInt()).isZero();
        long firstVersion = version(first), secondVersion = version(second), thirdVersion = version(c);
        JsonNode swapped = reorder(project, "ready", second, "up", duplicate, 200);
        assertThat(ids(swapped)).containsExactly(second, first, c);
        assertRow(second, "ready", 0, secondVersion + 1);
        assertRow(first, "ready", 1, firstVersion + 1);
        assertRow(c, "ready", 2, thirdVersion + 1);

        jdbc.update("update board_cards set position=-8 where id=?", second);
        jdbc.update("update board_cards set position=0 where id=?", first);
        JsonNode negative = order(project, "ready");
        List<Map<String, Object>> before = rows(project);
        reorder(project, "ready", second, "up", negative, 200);
        assertThat(rows(project)).isEqualTo(before);
        JsonNode moved = reorder(project, "ready", second, "down", negative, 200);
        assertThat(ids(moved).get(0)).isEqualTo(first);
        assertRow(first, "ready", 0, firstVersion + 2);
        assertRow(second, "ready", 1, secondVersion + 2);
    }

    @Test
    void validationNotFoundAndStaleSnapshotNeverWrite() throws Exception {
        String project = project();
        String other = project();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        JsonNode snapshot = order(project, "ready");
        List<Map<String, Object>> before = rows(project);
        mvc.perform(get(orderUrl("missing-project", "ready")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(get(orderUrl(project, "invalid")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.columnKey").exists());
        for (String body : List.of("{", "{}", "{\"direction\":\"sideways\",\"expectedOrder\":[]}")) {
            mvc.perform(post(reorderUrl(project, "ready", a)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        ObjectNode duplicate = request("up", snapshot);
        duplicate.withArray("expectedOrder").add(snapshot.path("cards").get(0));
        reorderBody(project, "ready", a, duplicate, 400, "VALIDATION_ERROR");
        ObjectNode fractional = request("up", snapshot);
        ((ObjectNode) fractional.withArray("expectedOrder").get(0)).put("position", 0.5);
        reorderBody(project, "ready", a, fractional, 400, "VALIDATION_ERROR");
        ObjectNode fractionalVersion = request("up", snapshot);
        ((ObjectNode) fractionalVersion.withArray("expectedOrder").get(0)).put("version", 0.5);
        reorderBody(project, "ready", a, fractionalVersion, 400, "VALIDATION_ERROR");
        ObjectNode negativeVersion = request("up", snapshot);
        ((ObjectNode) negativeVersion.withArray("expectedOrder").get(0)).put("version", -1);
        reorderBody(project, "ready", a, negativeVersion, 400, "VALIDATION_ERROR");
        ObjectNode missingPosition = request("up", snapshot);
        ((ObjectNode) missingPosition.withArray("expectedOrder").get(0)).remove("position");
        reorderBody(project, "ready", a, missingPosition, 400, "VALIDATION_ERROR");
        ObjectNode withoutTarget = request("up", snapshot);
        withoutTarget.withArray("expectedOrder").remove(0);
        reorderBody(project, "ready", a, withoutTarget, 400, "VALIDATION_ERROR");
        ObjectNode stalePosition = request("up", snapshot);
        ((ObjectNode) stalePosition.withArray("expectedOrder").get(0)).put("position", 99);
        reorderBody(project, "ready", b, stalePosition, 409, "BOARD_ORDER_CONFLICT");
        ObjectNode staleOrder = request("up", snapshot);
        staleOrder.set("expectedOrder", json.createArrayNode()
            .add(snapshot.path("cards").get(1)).add(snapshot.path("cards").get(0)));
        reorderBody(project, "ready", b, staleOrder, 409, "BOARD_ORDER_CONFLICT");
        reorder(project, "ready", "missing-card", "up", snapshot, 400);
        reorderBody(project, "ready", a, request("up", snapshot), 200, null);
        assertThat(rows(project)).isEqualTo(before);
        reorderBody(other, "ready", a, request("up", snapshot), 404, "BOARD_CARD_NOT_FOUND");
        reorderBody(project, "done", a, request("up", snapshot), 409, "BOARD_ORDER_CONFLICT");
        JsonNode moved = reorder(project, "ready", b, "up", snapshot, 200);
        assertThat(moved.path("changed").asBoolean()).isTrue();
        List<Map<String, Object>> committed = rows(project);
        reorderBody(project, "ready", b, request("up", snapshot), 409, "BOARD_ORDER_CONFLICT");
        assertThat(rows(project)).isEqualTo(committed);
    }

    @Test
    void newOrDeletedNeighborAndOldPutUseTheirExistingContracts() throws Exception {
        String project = project();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        JsonNode beforeAdd = order(project, "ready");
        String added = card(project, "ready", "Added");
        reorderBody(project, "ready", b, request("up", beforeAdd), 409, "BOARD_ORDER_CONFLICT");
        JsonNode beforeDelete = order(project, "ready");
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, added)
                .param("expectedVersion", "0"))
            .andExpect(status().isNoContent());
        reorderBody(project, "ready", b, request("up", beforeDelete), 409, "BOARD_ORDER_CONFLICT");
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, b)
                .param("expectedVersion", "0"))
            .andExpect(status().isNoContent());
        reorderBody(project, "ready", b, request("up", order(project, "ready")), 400, "VALIDATION_ERROR");
        reorderBody(project, "ready", b, request("up", beforeDelete), 404, "BOARD_CARD_NOT_FOUND");
        String c = card(project, "ready", "C");
        JsonNode fresh = order(project, "ready");
        JsonNode changed = reorder(project, "ready", c, "up", fresh, 200);
        mvc.perform(delete("/api/projects/{project}/board/cards/{id}", project, c)
                .param("expectedVersion", "0"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BOARD_CARD_VERSION_CONFLICT"));
        ObjectNode stalePut = cardBody("Stale PUT", "ready");
        stalePut.put("expectedVersion", 0);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, c)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(stalePut)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOARD_CARD_VERSION_CONFLICT"));
        assertThat(changed.path("changed").asBoolean()).isTrue();
        assertThat(rows(project)).anySatisfy(row -> assertThat(row).containsEntry("ID", a));
    }

    @Test
    void editedNeighborAndMovedTargetInvalidateTheWholeColumnSnapshot() throws Exception {
        String project = project();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        JsonNode beforeEdit = order(project, "ready");
        ObjectNode edit = cardBody("Edited A", "ready");
        edit.put("expectedVersion", 0);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, a)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(edit)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        List<Map<String, Object>> afterEdit = rows(project);
        reorderBody(project, "ready", b, request("up", beforeEdit), 409, "BOARD_ORDER_CONFLICT");
        assertThat(rows(project)).isEqualTo(afterEdit);

        JsonNode beforeMove = order(project, "ready");
        ObjectNode move = cardBody("Moved B", "done");
        move.put("expectedVersion", 0);
        mvc.perform(put("/api/projects/{project}/board/cards/{id}", project, b)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(move)))
            .andExpect(status().isOk());
        List<Map<String, Object>> afterMove = rows(project);
        reorderBody(project, "ready", b, request("up", beforeMove), 409, "BOARD_ORDER_CONFLICT");
        assertThat(rows(project)).isEqualTo(afterMove);
        assertRow(b, "done", 0, 1);
    }

    @Test
    void creationInSameOrOtherColumnSerializesWithoutLostCards() throws Exception {
        String project = project();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        for (String addedColumn : List.of("ready", "done")) {
            JsonNode snapshot = order(project, "ready");
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var reorder = pool.submit(() -> concurrentReorder(project, b, "up", snapshot, ready, start));
                var create = pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                        return card(project, addedColumn, "Added " + addedColumn);
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                });
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                int reorderStatus = reorder.get(10, TimeUnit.SECONDS);
                String created = create.get(10, TimeUnit.SECONDS);
                assertThat(created).isNotBlank();
                assertThat(reorderStatus).isIn(200, 409);
                if (addedColumn.equals("done")) assertThat(reorderStatus).isEqualTo(200);
                assertThat(rows(project)).anySatisfy(row -> assertThat(row).containsEntry("ID", created)
                    .containsEntry("COLUMN_KEY", addedColumn));
                assertThat(rows(project)).anySatisfy(row -> assertThat(row).containsEntry("ID", a));
                assertThat(rows(project)).anySatisfy(row -> assertThat(row).containsEntry("ID", b));
            } finally {
                start.countDown();
                pool.shutdownNow();
            }
        }
    }

    @Test
    void simultaneousReordersHaveOneWinnerAndReplayConflicts() throws Exception {
        String project = project();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        String c = card(project, "ready", "C");
        JsonNode snapshot = order(project, "ready");
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> concurrentReorder(project, b, "up", snapshot, ready, start));
            var second = pool.submit(() -> concurrentReorder(project, c, "up", snapshot, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
            List<String> winner = ids(order(project, "ready"));
            assertThat(winner).isIn(List.of(b, a, c), List.of(a, c, b));
            List<Map<String, Object>> committed = rows(project);
            reorderBody(project, "ready", b, request("up", snapshot), 409, "BOARD_ORDER_CONFLICT");
            assertThat(rows(project)).isEqualTo(committed);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void failedSecondPositionUpdateRollsBackEveryPositionAndVersion() throws Exception {
        String project = project();
        String a = card(project, "ready", "A");
        String b = card(project, "ready", "B");
        String c = card(project, "ready", "C");
        jdbc.update("update board_cards set position=10 where id=?", a);
        jdbc.update("update board_cards set position=20 where id=?", b);
        jdbc.update("update board_cards set position=30 where id=?", c);
        JsonNode snapshot = order(project, "ready");
        List<Map<String, Object>> before = rows(project);
        jdbc.execute("alter table board_cards add constraint pm_reorder_test_fail check "
            + "(project_id <> '" + project + "' or position <> 1)");
        try {
            mvc.perform(post(reorderUrl(project, "ready", b)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request("up", snapshot))))
                .andExpect(status().isInternalServerError());
            assertThat(rows(project)).isEqualTo(before);
        } finally {
            jdbc.execute("alter table board_cards drop constraint pm_reorder_test_fail");
        }
    }

    private int concurrentReorder(String project, String id, String direction, JsonNode snapshot,
                                  CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
            return mvc.perform(post(reorderUrl(project, "ready", id)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request(direction, snapshot))))
                .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String project() throws Exception {
        String body = """
            {"name":"Order project","owner":"Owner","status":"active","health":"green",
             "deliveryModel":"kanban","quarter":"Q4","deadline":"2026-12-31","milestone":"Gate",
             "risk":"Risk","dependency":"Dependency","kpiName":"KPI","kpiTarget":"Target","summary":"Review"}
            """;
        MvcResult result = mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private String card(String project, String column, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects/{id}/board/cards", project)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cardBody(title, column))))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private ObjectNode cardBody(String title, String column) {
        ObjectNode body = json.createObjectNode();
        body.put("title", title); body.put("description", "Description"); body.put("owner", "Owner");
        body.put("dueDate", "2026-12-31"); body.put("columnKey", column); body.put("priority", "medium");
        body.put("labels", "review"); body.put("estimate", 1); body.put("blocked", false);
        return body;
    }

    private JsonNode order(String project, String column) throws Exception {
        MvcResult result = mvc.perform(get(orderUrl(project, column))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode reorder(String project, String column, String id, String direction,
                             JsonNode snapshot, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post(reorderUrl(project, column, id)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request(direction, snapshot))))
            .andExpect(status().is(expectedStatus)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void reorderBody(String project, String column, String id, JsonNode body,
                             int expectedStatus, String expectedCode) throws Exception {
        MvcResult result = mvc.perform(post(reorderUrl(project, column, id)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)))
            .andExpect(status().is(expectedStatus)).andReturn();
        if (expectedCode != null) {
            assertThat(json.readTree(result.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo(expectedCode);
        }
    }

    private ObjectNode request(String direction, JsonNode snapshot) {
        ObjectNode body = json.createObjectNode();
        body.put("direction", direction);
        body.set("expectedOrder", snapshot.path("cards").deepCopy());
        return body;
    }

    private List<String> ids(JsonNode response) {
        List<String> result = new ArrayList<>();
        response.path("cards").forEach(card -> result.add(card.path("id").asText()));
        return result;
    }

    private List<Map<String, Object>> rows(String project) {
        return jdbc.queryForList("select * from board_cards where project_id=? order by id", project);
    }

    private long version(String id) {
        return jdbc.queryForObject("select version from board_cards where id=?", Long.class, id);
    }

    private void assertRow(String id, String column, int position, long version) {
        Map<String, Object> row = jdbc.queryForMap("select * from board_cards where id=?", id);
        assertThat(row).containsEntry("COLUMN_KEY", column).containsEntry("POSITION", position)
            .containsEntry("VERSION", version);
    }

    private String orderUrl(String project, String column) {
        return "/api/projects/" + project + "/board/kanban/columns/" + column + "/order";
    }

    private String reorderUrl(String project, String column, String id) {
        return "/api/projects/" + project + "/board/kanban/columns/" + column + "/cards/" + id + "/reorder";
    }
}
