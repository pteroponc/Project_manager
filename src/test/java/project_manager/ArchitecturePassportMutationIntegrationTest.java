package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import project_manager.service.*;
import project_manager.web.ArchitectureMutationException;
import project_manager.web.ProjectConflictException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_passport_write;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ArchitecturePassportMutationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ArchitecturePassportMutationService mutations;
    @Autowired ProjectDeletionService deletion;
    @Autowired PlatformTransactionManager manager;
    @TestBean(name = "architectureAssessmentClock", methodName = "mutableClock", enforceOverride = true) Clock clock;
    @MockitoSpyBean ArchitecturePassportSetWriter sets;
    @Autowired ArchitectureReadService reads;
    AtomicReference<Instant> now;
    private static final String DRAFT = "{\"expectedProjectVersion\":0,\"criticality\":\"HIGH\",\"stage\":\"NOT_STARTED\",\"solutionType\":\"NEW_SOLUTION\"}";
    private static final List<String> TABLES = List.of("project_integration_data_classes", "project_architecture_components",
        "project_architecture_integrations", "project_architecture_checks", "project_security_risks",
        "project_architecture_data_classes", "project_architecture_environments", "project_architecture_passports",
        "project_milestones", "board_cards", "release_trains", "projects");

    @BeforeEach
    void resetState() {
        reset(sets);
        now = ((MutableClock) clock).now;
        now.set(Instant.parse("2026-10-14T09:00:00Z"));
        TABLES.forEach(table -> jdbc.update("delete from " + table));
        project("p"); project("other");
    }

    @Test
    void draftCreationIncrementsOnlyProjectVersionAndDoesNotInitializeChildren() throws Exception {
        var before = snapshot();
        JsonNode result = call(post("/api/projects/p/architecture"), DRAFT, 201);
        assertThat(result.at("/project/version").asLong()).isEqualTo(1);
        assertThat(result.at("/passport/version").asLong()).isZero();
        assertThat(result.at("/passport/createdAt").asText()).isEqualTo(now.get().toString());
        assertThat(result.at("/passport/updatedAt")).isEqualTo(result.at("/passport/createdAt"));
        assertThat(result.at("/passport/architect").isNull()).isTrue();
        assertThat(result.at("/assessment/status").asText()).isEqualTo("NOT_ASSESSED");
        for (String field : List.of("dataClasses", "environments", "components", "integrations", "checks", "risks"))
            assertThat(result.path(field).size()).as(field).isZero();
        JsonNode conflict = call(post("/api/projects/p/architecture"), DRAFT, 409);
        assertThat(conflict.path("code").asText()).isEqualTo("PASSPORT_ALREADY_EXISTS");
        assertThat(conflict.path("currentVersion").asLong()).isZero();
        assertThat(conflict.path("versionResource").asText()).isEqualTo("PASSPORT");
        assertThat(jdbc.queryForObject("select version from projects where id='p'", Long.class)).isEqualTo(1);
        assertThat(snapshot().get("projects").stream().filter(row -> row.get("ID").equals("other")).toList())
            .isEqualTo(before.get("projects").stream().filter(row -> row.get("ID").equals("other")).toList());
    }

    @Test
    void fullCreationNormalizesNfcAndMultilineTextAndPatchPreservesOmittedFields() throws Exception {
        JsonNode result = call(post("/api/projects/p/architecture"), full(), 201);
        assertThat(result.at("/passport/architect").asText()).isEqualTo("Zoé");
        assertThat(result.at("/passport/solutionSummary").asText()).isEqualTo("First line\nSecond line");
        assertThat(result.path("dataClasses").toString()).isEqualTo("[\"INTERNAL\",\"PUBLIC\"]");
        assertThat(result.path("environments").toString()).isEqualTo("[\"PROD\",\"TEST\"]");
        assertThat(result.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        now.set(now.get().plusSeconds(60));
        JsonNode patched = call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":0,\"stage\":\"IN_REVIEW\"}", 200);
        assertThat(patched.at("/passport/stage").asText()).isEqualTo("IN_REVIEW");
        assertThat(patched.at("/passport/architect")).isEqualTo(result.at("/passport/architect"));
        assertThat(patched.at("/passport/solutionSummary")).isEqualTo(result.at("/passport/solutionSummary"));
        assertThat(patched.at("/passport/lastReviewDate")).isEqualTo(result.at("/passport/lastReviewDate"));
        assertThat(patched.path("dataClasses")).isEqualTo(result.path("dataClasses"));
        assertThat(patched.path("environments")).isEqualTo(result.path("environments"));
        assertThat(patched.at("/passport/version").asLong()).isEqualTo(1);
        assertThat(patched.at("/project/version").asLong()).isEqualTo(1);
        assertThat(patched.at("/passport/createdAt")).isEqualTo(result.at("/passport/createdAt"));
        assertThat(patched.at("/passport/updatedAt").asText()).isEqualTo(now.get().toString());
    }

    @Test
    void patchClearsNullableFieldsAndReplacesOrClearsSetsAtomically() throws Exception {
        call(post("/api/projects/p/architecture"), full(), 201);
        JsonNode replaced = call(patch("/api/projects/p/architecture"), """
            {"expectedVersion":0,"architect":null,"solutionSummary":null,"availabilityRequirement":null,
             "recoveryRequirement":null,"lastReviewDate":null,"dataClasses":["PERSONAL"],"environments":[]}
            """, 200);
        for (String field : List.of("architect", "solutionSummary", "availabilityRequirement", "recoveryRequirement", "lastReviewDate"))
            assertThat(replaced.at("/passport/" + field).isNull()).as(field).isTrue();
        assertThat(replaced.path("dataClasses").toString()).isEqualTo("[\"PERSONAL\"]");
        assertThat(replaced.path("environments").size()).isZero();
        assertThat(replaced.at("/passport/version").asLong()).isEqualTo(1);
        JsonNode cleared = call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":1,\"dataClasses\":[],\"environments\":[\"DEV\"]}", 200);
        assertThat(cleared.path("dataClasses").size()).isZero();
        assertThat(cleared.path("environments").toString()).isEqualTo("[\"DEV\"]");
        assertThat(cleared.at("/passport/version").asLong()).isEqualTo(2);
    }

    @Test
    void identicalPatchWithFixedClockStillIncrementsExactlyOnce() throws Exception {
        JsonNode first = call(post("/api/projects/p/architecture"), full(), 201);
        for (int version = 0; version < 3; version++) {
            JsonNode result = call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":" + version
                + ",\"stage\":\"APPROVED\",\"dataClasses\":[\"PUBLIC\",\"INTERNAL\"],\"environments\":[\"TEST\",\"PROD\"]}", 200);
            assertThat(result.at("/passport/version").asLong()).isEqualTo(version + 1);
            assertThat(result.at("/project/version").asLong()).isEqualTo(1);
            assertThat(result.at("/passport/createdAt")).isEqualTo(first.at("/passport/createdAt"));
            assertThat(result.at("/passport/updatedAt").asText()).isEqualTo(now.get().toString());
        }
    }

    @Test
    void normalizedLengthBoundariesAndCompleteClosedSetsAreAccepted() throws Exception {
        var body = node(full()).deepCopy();
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) body;
        object.put("architect", "e\u0301".repeat(255)); // 510 UTF-16 units before NFC, 255 after.
        object.put("solutionSummary", "x".repeat(2000));
        object.put("availabilityRequirement", "x".repeat(1000)); object.put("recoveryRequirement", "x".repeat(1000));
        object.putArray("dataClasses").add("PUBLIC").add("INTERNAL").add("CONFIDENTIAL").add("PERSONAL").add("PAYMENT").add("COMMERCIAL_SECRET");
        object.putArray("environments").add("DEV").add("TEST").add("PREPROD").add("PROD").add("OTHER");
        JsonNode result = call(post("/api/projects/p/architecture"), body.toString(), 201);
        assertThat(result.at("/passport/architect").asText()).isEqualTo("é".repeat(255));
        assertThat(result.at("/passport/solutionSummary").asText()).hasSize(2000);
        assertThat(result.path("dataClasses").size()).isEqualTo(6); assertThat(result.path("environments").size()).isEqualTo(5);
    }

    @Test
    void missingResourcesAndStaleVersionsHaveRealVersionAndResourceKind() throws Exception {
        call(post("/api/projects/missing/architecture"), DRAFT, 404);
        assertThat(call(patch("/api/projects/missing/architecture"), "{\"expectedVersion\":0,\"stage\":\"APPROVED\"}", 404).path("code").asText()).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":0,\"stage\":\"APPROVED\"}", 404).path("code").asText()).isEqualTo("PASSPORT_NOT_FOUND");
        jdbc.update("update projects set version=5 where id='p'");
        var before = snapshot();
        JsonNode stale = call(post("/api/projects/p/architecture"), DRAFT, 409);
        assertThat(stale.path("currentVersion").asLong()).isEqualTo(5);
        assertThat(stale.path("versionResource").asText()).isEqualTo("PROJECT");
        assertThat(stale.path("code").asText()).isEqualTo("PROJECT_VERSION_CONFLICT");
        assertThat(snapshot()).isEqualTo(before);
        call(post("/api/projects/p/architecture"), DRAFT.replace("Version\":0", "Version\":5"), 201);
        call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":0,\"stage\":\"APPROVED\"}", 200);
        before = snapshot();
        JsonNode conflict = call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":0,\"stage\":\"IN_DESIGN\"}", 409);
        assertThat(conflict.path("currentVersion").asLong()).isEqualTo(1);
        assertThat(conflict.path("versionResource").asText()).isEqualTo("PASSPORT");
        assertThat(conflict.path("code").asText()).isEqualTo("PASSPORT_VERSION_CONFLICT");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void malformedAndForbiddenPatchInputsAreSafeAndNeverChangeRows() throws Exception {
        call(post("/api/projects/p/architecture"), full(), 201);
        var before = snapshot();
        List<String> bodies = new ArrayList<>(List.of("null", "[]", "1", "{}", "{\"expectedVersion\":0}",
            "{\"expectedVersion\":0,\"expectedVersion\":0,\"stage\":\"APPROVED\"}",
            "{\"expectedVersion\":0,\"architect\":\"First\",\"architect\":\"Second\"}",
            "{\"expectedVersion\":0,\"stage\":\"APPROVED\"} {}", "{", "{\"SQL-secret-field\":1}"));
        for (String value : List.of("-1", "0.0", "\"0\"", "true", "null", "9223372036854775808"))
            bodies.add("{\"expectedVersion\":" + value + ",\"stage\":\"APPROVED\"}");
        for (String field : List.of("id", "projectId", "version", "createdAt", "updatedAt", "assessment", "checks", "components", "risks", "integrations"))
            bodies.add("{\"expectedVersion\":0,\"" + field + "\":null}");
        for (String field : List.of("dataClasses", "environments")) {
            for (String value : List.of("null", "\"PUBLIC\"", "[null]", "[1]", "[\"UNKNOWN\"]", "[\"PUBLIC\",\"PUBLIC\"]", "[\"public\"]"))
                bodies.add("{\"expectedVersion\":0,\"" + field + "\":" + value + "}");
        }
        for (String value : List.of("null", "\"approved\"", "1", "true", "[]", "{}"))
            bodies.add("{\"expectedVersion\":0,\"stage\":" + value + "}");
        for (String value : List.of("\"2026-02-30\"", "\"14.10.2026\"", "2026", "[]", "true"))
            bodies.add("{\"expectedVersion\":0,\"lastReviewDate\":" + value + "}");
        for (String field : List.of("architect", "solutionSummary", "availabilityRequirement", "recoveryRequirement")) {
            bodies.add("{\"expectedVersion\":0,\"" + field + "\":5}");
            bodies.add("{\"expectedVersion\":0,\"" + field + "\":\"   \"}");
        }
        for (String value : List.of("<script>alert(1)</script>", "onerror=alert(1)", "javascript:alert(1)", "eval('x')",
            "&lt;b&gt;secret-value&lt;/b&gt;", "`echo secret-value`", "DROP TABLE projects", "jdbc:h2:mem:secret-value",
            "mailto:secret-value@example.com", "https://secret.example", "www.example.ru", "192.168.1.2", "2001:db8::1", "password=secret-value", "DB_PASSWORD=secret-value",
            "Bearer secret-value", "-----BEGIN PRIVATE KEY-----", "ghp_0123456789abcdef", "bad\u0000control", "bad\u202Etext", "bad\ttab"))
            bodies.add(json.createObjectNode().put("expectedVersion",0).put("solutionSummary",value).toString());
        for (String body : bodies) {
            JsonNode error = call(patch("/api/projects/p/architecture"), body, 400);
            assertThat(error.toString()).doesNotContain("secret-value", "SQL-secret-field", "<script>", "192.168", "java.", "stackTrace");
        }
        for (String field : List.of("architect", "solutionSummary", "availabilityRequirement", "recoveryRequirement")) {
            int limit = field.equals("architect") ? 255 : field.equals("solutionSummary") ? 2000 : 1000;
            call(patch("/api/projects/p/architecture"), json.createObjectNode().put("expectedVersion",0).put(field,"x".repeat(limit+1)).toString(), 400);
        }
        call(patch("/api/projects/p/architecture"), " ".repeat(32769), 400);
        call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":0,\"dataClasses\":[\"PUBLIC\",\"INTERNAL\",\"CONFIDENTIAL\",\"PERSONAL\",\"PAYMENT\",\"COMMERCIAL_SECRET\",\"PUBLIC\"]}", 400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void invalidCreateDoesNotIncrementProjectOrCreateAnyRows() throws Exception {
        var before = snapshot();
        for (String body : List.of("{}", "{\"expectedProjectVersion\":0}", DRAFT.replace("\"HIGH\"", "null"),
            DRAFT.replace("NEW_SOLUTION", "unknown"), DRAFT.replace("\"NOT_STARTED\"", "[]"),
            DRAFT.replace("Version\":0", "Version\":\"0\""), DRAFT.substring(0,DRAFT.length()-1)+",\"dataClasses\":null}"))
            call(post("/api/projects/p/architecture"),body,400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void failureBetweenFieldsAndSetsRollsBackFieldsBothSetsVersionsAndDates() throws Exception {
        call(post("/api/projects/p/architecture"), full(), 201);
        var before = snapshot(); now.set(now.get().plusSeconds(60));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("SQL secret-value java.sql at C:\\private"))
            .when(sets).environments(any(), any());
        JsonNode error = call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":0,\"architect\":\"Changed\",\"dataClasses\":[\"PERSONAL\"],\"environments\":[\"DEV\"]}", 500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR");
        assertThat(error.toString()).doesNotContain("secret-value", "java.sql", "C:");
        assertThat(snapshot()).isEqualTo(before);
        // Creation must also roll back the project version and passport already flushed.
        error = call(post("/api/projects/other/architecture"), full(), 500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aggregateIsBuiltWithinTheWriteTransactionAndSeesUncommittedRows() throws Exception {
        assertThatThrownBy(() -> reads.cardAfterWrite("p"))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Card result = new TransactionTemplate(manager).execute(status -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                Card own = mutations.create("p", node(full()));
                assertThat(own.passport().version()).isZero(); assertThat(own.dataClasses()).containsExactly("INTERNAL", "PUBLIC");
                assertThat(jdbc.queryForObject("select version from projects where id='p'", Long.class)).isEqualTo(own.project().version());
                try {
                    JsonNode external = executor.submit(() -> getCard()).get(5, TimeUnit.SECONDS);
                    assertThat(external.path("passport").isNull()).isTrue();
                } catch (Exception error) { throw new AssertionError(error); }
                return own;
            });
            assertThat(getCard()).isEqualTo(json.readTree(json.writeValueAsBytes(result)));
        }
    }

    @Test
    void oldProjectsTasksTrainsAndMilestonesAreUnchangedExceptCreationVersion() throws Exception {
        jdbc.update("insert into board_cards (id,project_id,column_key,position,title,description,owner,due_date,estimate,blocked) values ('card','p','backlog',0,'Task','Description','Owner','',0,false)");
        jdbc.update("insert into project_milestones (id,project_id,name,planned_date,completed,position) values ('milestone','p','Gate','2026-10-01',false,0)");
        jdbc.update("insert into release_trains (id,name,status,cadence,quarter,planned_release_date,code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) values ('train','Train','planned','monthly','Q1','','','','',0,0,0,0,'Scope','Risk','Decision')");
        var before = snapshot();
        call(post("/api/projects/p/architecture"), full(), 201);
        call(patch("/api/projects/p/architecture"), "{\"expectedVersion\":0,\"architect\":null,\"dataClasses\":[]}", 200);
        var after = snapshot();
        for (String table : List.of("board_cards", "release_trains", "project_milestones")) assertThat(after.get(table)).isEqualTo(before.get(table));
        before.get("projects").stream().filter(row -> row.get("ID").equals("p")).forEach(row -> row.put("VERSION",1L));
        assertThat(after.get("projects")).isEqualTo(before.get("projects"));
    }

    @Test
    void concurrentPostsWaitOnProjectLockAndLeaveOnePassportOneConflict() throws Exception {
        concurrentWrite(false);
    }
    @Test
    void concurrentPatchesWaitOnPassportLockAndSameVersionHasOneWinner() throws Exception {
        call(post("/api/projects/p/architecture"), DRAFT, 201);
        concurrentWrite(true);
    }
    private void concurrentWrite(boolean patch) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode card = uncheckedCall(patch ? patch("/api/projects/p/architecture") : post("/api/projects/p/architecture"),
                    patch ? "{\"expectedVersion\":0,\"architect\":\"Winner\"}" : DRAFT, patch ? 200 : 201);
                written.countDown(); await(release); return card;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    return uncheckedCall(patch ? patch("/api/projects/p/architecture") : post("/api/projects/p/architecture"),
                        patch ? "{\"expectedVersion\":0,\"architect\":\"Loser\"}" : DRAFT, 409);
                });
                blocked(patch ? "project_architecture_passports" : "projects", second);
                release.countDown(); first.get(5,TimeUnit.SECONDS);
                JsonNode conflict = second.get(5,TimeUnit.SECONDS);
                assertThat(conflict.path("code").asText()).isEqualTo(patch ? "PASSPORT_VERSION_CONFLICT" : "PASSPORT_ALREADY_EXISTS");
                assertThat(conflict.path("currentVersion").asLong()).isEqualTo(patch ? 1L : 0L);
                assertThat(conflict.path("versionResource").asText()).isEqualTo("PASSPORT");
                assertThat(jdbc.queryForObject("select count(*) from project_architecture_passports",Long.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select version from projects where id='p'",Long.class)).isEqualTo(1);
                if (patch) assertThat(jdbc.queryForObject("select architect from project_architecture_passports where project_id='p'",String.class)).isEqualTo("Winner");
                noOrphans();
            } finally { release.countDown(); }
        }
    }

    @Test
    void createLockFirstMakesDeleteWaitAndConflictWithoutOrphans() throws Exception { createVersusDelete(false); }
    @Test
    void deleteLockFirstMakesCreateWaitAndReturn404WithoutOrphans() throws Exception { createVersusDelete(true); }
    private void createVersusDelete(boolean deleteFirst) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                if (deleteFirst) deletion.delete("p"); else uncheckedCall(post("/api/projects/p/architecture"), full(), 201);
                written.countDown(); await(release);
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    return uncheckedCall(deleteFirst ? post("/api/projects/p/architecture") : delete("/api/projects/p"),
                        deleteFirst ? full() : "", deleteFirst ? 404 : 409);
                });
                blocked("projects", second);
                release.countDown(); first.get(5,TimeUnit.SECONDS);
                JsonNode error = second.get(5,TimeUnit.SECONDS);
                assertThat(error.path("code").asText()).isEqualTo(deleteFirst ? "PROJECT_NOT_FOUND" : "PROJECT_HAS_DEPENDENCIES");
                assertThat(jdbc.queryForObject("select count(*) from projects where id='p'",Long.class)).isEqualTo(deleteFirst ? 0 : 1);
                assertThat(jdbc.queryForObject("select count(*) from project_architecture_passports where project_id='p'",Long.class)).isEqualTo(deleteFirst ? 0 : 1);
                noOrphans();
            } finally { release.countDown(); }
        }
    }

    private void blocked(String table, Future<?> second) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var rows = jdbc.queryForList("select session_state,blocker_id,executing_statement from information_schema.sessions");
            if (rows.stream().anyMatch(row -> "BLOCKED".equals(row.get("SESSION_STATE")) && row.get("BLOCKER_ID") != null
                && String.valueOf(row.get("EXECUTING_STATEMENT")).toLowerCase().contains(table))) {
                assertThat(second.isDone()).isFalse(); return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
        }
        throw new AssertionError("Second transaction did not wait on expected lock");
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private void noOrphans() {
        assertThat(jdbc.queryForObject("select count(*) from project_architecture_passports a left join projects p on p.id=a.project_id where p.id is null",Long.class)).isZero();
        for (String table : List.of("project_architecture_data_classes", "project_architecture_environments"))
            assertThat(jdbc.queryForObject("select count(*) from " + table + " d left join project_architecture_passports a on a.project_id=d.project_id where a.project_id is null",Long.class)).isZero();
    }
    private JsonNode call(MockHttpServletRequestBuilder request, String body, int expected) throws Exception {
        var result = mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(expected);
        return json.readTree(result.getContentAsString());
    }
    private JsonNode uncheckedCall(MockHttpServletRequestBuilder request, String body, int expected) {
        try { return call(request, body, expected); } catch (Exception error) { throw new AssertionError(error); }
    }
    private JsonNode getCard() throws Exception { return json.readTree(mvc.perform(get("/api/projects/p/architecture")).andReturn().getResponse().getContentAsString()); }
    private JsonNode node(String body) { try { return json.readTree(body); } catch (Exception error) { throw new AssertionError(error); } }
    private Map<String,List<Map<String,Object>>> snapshot() {
        var result = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table : TABLES) result.put(table,jdbc.queryForList("select * from " + table).stream().sorted(java.util.Comparator.comparing(Object::toString)).toList());
        return result;
    }
    private void project(String id) {
        jdbc.update("insert into projects(id,name,owner,status,health,budget,progress,quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,version) values (?,?,'Owner','active','green',2147483647,0,'Q1','','','','','','','Summary',0)",id,id);
    }
    private static String full() { return """
        {"expectedProjectVersion":0,"criticality":"HIGH","stage":"APPROVED","solutionType":"NEW_SOLUTION",
         "architect":" Zoé ","solutionSummary":" First line\\r\\nSecond line ","availabilityRequirement":"99.9%",
         "recoveryRequirement":"1 hour","lastReviewDate":"2026-10-01","dataClasses":["PUBLIC","INTERNAL"],"environments":["TEST","PROD"]}
        """; }
    static Clock mutableClock() { return new MutableClock(new AtomicReference<>(Instant.parse("2026-10-14T09:00:00Z")),ZoneOffset.UTC); }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> now;
        final ZoneId zone;
        MutableClock(AtomicReference<Instant> now, ZoneId zone) { this.now = now; this.zone = zone; }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(now,zone); }
    }
}
