package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import project_manager.service.ArchitectureReadinessAssessmentService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Every connection belongs to this disposable memory database; tests have no outer transaction. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:pmtest_architecture_read;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
class ArchitectureReadApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;
    @Autowired ReadInspector inspector;
    @Autowired CountingAssessment assessments;

    private static final List<String> TABLES = List.of("project_integration_data_classes",
        "project_architecture_components", "project_architecture_integrations", "project_architecture_checks",
        "project_security_risks", "project_architecture_data_classes", "project_architecture_environments",
        "project_architecture_passports", "project_milestones", "board_cards", "release_trains", "projects");

    @BeforeEach
    void reset() {
        inspector.checked = false; inspector.pause = false; inspector.fail = false; inspector.statements.clear();
        TABLES.forEach(table -> jdbc.update("delete from " + table));
        assessments.contextCalls.set(0);
    }

    @Test
    void missingProjectAndMissingPassportAreDifferentAndNeverCreateRows() throws Exception {
        for (String suffix : List.of("/architecture", "/architecture/deletion-impact", "/deletion-impact")) {
            JsonNode error = request("/api/projects/missing" + suffix, 404);
            assertThat(error.path("code").asText()).isEqualTo("PROJECT_NOT_FOUND");
            assertThat(error.path("error").asText()).isEqualTo("Project not found");
        }
        project("empty", "Empty", "Owner", "Q1", "planned");
        var before = snapshot();
        JsonNode card = ok("/api/projects/empty/architecture");
        assertThat(card.path("passport").isNull()).isTrue();
        for (String key : List.of("dataClasses", "environments", "components", "integrations", "checks", "risks"))
            assertThat(card.path(key).size()).as(key).isZero();
        assertThat(card.at("/assessment/status").asText()).isEqualTo("NOT_ASSESSED");
        assertThat(codes(card.path("assessment"))).contains("PASSPORT_MISSING");
        JsonNode impact = ok("/api/projects/empty/architecture/deletion-impact");
        assertThat(impact.path("passportPresent").asBoolean()).isFalse();
        assertThat(impact.path("deletionAllowed").asBoolean()).isFalse();
        assertThat(impact.path("blockers").size()).isEqualTo(1);
        JsonNode portfolio = ok("/api/architecture/portfolio?criticality=unspecified");
        assertThat(ids(portfolio)).containsExactly("empty");
        assertThat(portfolio.at("/items/0/criticality").isNull()).isTrue();
        assertThat(ok("/api/projects/empty/deletion-impact").path("deletionAllowed").asBoolean()).isTrue();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void fullCardReturnsLinksNullableFieldsVersionsAndComputedRiskWithoutBudget() throws Exception {
        project("full", "Full", "Owner", "Q1", "active"); ready("full", "HIGH"); details("full");
        JsonNode card = ok("/api/projects/full/architecture");
        assertThat(card.at("/project/version").asLong()).isEqualTo(7);
        assertThat(card.at("/passport/version").asLong()).isEqualTo(8);
        assertThat(card.at("/passport/createdAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(card.path("dataClasses").toString()).isEqualTo("[\"INTERNAL\",\"PUBLIC\"]");
        assertThat(card.path("environments").toString()).isEqualTo("[\"PROD\"]");
        assertThat(card.at("/components/0/projectId").asText()).isEqualTo("full");
        assertThat(card.at("/components/0/platform").isNull()).isTrue();
        assertThat(card.at("/components/0/version").asLong()).isEqualTo(9);
        assertThat(card.at("/integrations/0/dataClasses").toString()).isEqualTo("[\"PERSONAL\",\"PUBLIC\"]");
        assertThat(card.at("/integrations/0/version").asLong()).isEqualTo(10);
        assertThat(card.at("/checks/0/version").asLong()).isEqualTo(11);
        assertThat(card.path("checks").size()).isEqualTo(10);
        assertThat(card.at("/risks/0/version").asLong()).isEqualTo(12);
        assertThat(card.at("/risks/0/level").asText()).isEqualTo("HIGH");
        assertThat(card.at("/risks/0/acceptanceVerification").asText()).isEqualTo("UNVERIFIED");
        assertThat(card.at("/assessment/acceptedHighRisks/0/justification").asText()).isEqualTo("Business acceptance");
        assertThat(card.at("/assessment/acceptedHighRisks/0/acceptanceVerification").asText()).isEqualTo("UNVERIFIED");
        assertThat(card.at("/assessment/status").asText()).isEqualTo("ATTENTION_REQUIRED");
        assertThat(card.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        assertThat(card.toString()).doesNotContain("budget");
        JsonNode portfolio = ok("/api/architecture/portfolio");
        assertThat(portfolio.at("/items/0/assessment")).isEqualTo(card.path("assessment"));
        assertThat(portfolio.at("/summary/acceptedHighRiskCount").asLong()).isEqualTo(1);
        assertThat(portfolio.toString()).doesNotContain("budget");
    }

    @Test
    void completeAndIncompletePassportsKeepEveryPriorityReasonAndUpcomingCheck() throws Exception {
        project("ready", "Ready", "Owner", "Q1", "paused"); ready("ready", "LOW");
        assertThat(ok("/api/projects/ready/architecture").at("/assessment/status").asText()).isEqualTo("READY");
        project("incomplete", "Incomplete", "Owner", "Q1", "done"); passport("incomplete", "CRITICAL", "CHANGES_REQUIRED", false);
        jdbc.update("insert into project_architecture_checks (id,project_id,check_type,required,status,planned_date,version) values (?,?,?,?,?,?,?)",
            "pending", "incomplete", "THREAT_MODEL", true, "IN_PROGRESS", "2026-10-14", 3);
        risk("blocked", "incomplete", "CRITICAL", "CRITICAL", "OPEN");
        risk("accepted", "incomplete", "LOW", "CRITICAL", "ACCEPTED");
        JsonNode assessment = ok("/api/projects/incomplete/architecture").path("assessment");
        assertThat(assessment.path("status").asText()).isEqualTo("BLOCKED");
        assertThat(assessment.path("reasons").findValuesAsText("status"))
            .contains("BLOCKED", "ATTENTION_REQUIRED", "NOT_ASSESSED");
        assertThat(codes(assessment)).contains("REQUIRED_INFORMATION_INCOMPLETE", "CHECKS_INCOMPLETE");
        assertThat(assessment.path("upcomingChecks").size()).isEqualTo(1);
        assertThat(assessment.path("acceptedHighRisks").size()).isEqualTo(1);
        assertThat(ok("/api/architecture/portfolio?query=incomplete").at("/items/0/assessment")).isEqualTo(assessment);
    }

    @Test
    void intersectedFiltersSearchAndPaginationUseFullFilteredAggregates() throws Exception {
        portfolioFixtures();
        JsonNode filtered = ok("/api/architecture/portfolio?query=ALICE&quarter=q1&projectStatus=ACTIVE&readiness=READY&criticality=HIGH");
        assertThat(ids(filtered)).containsExactly("c");
        assertThat(ok("/api/architecture/portfolio?query=alpha").path("totalCount").asLong()).isEqualTo(2);
        // Summary text is deliberately not searchable.
        assertThat(ok("/api/architecture/portfolio?query=hidden-summary").path("totalCount").asLong()).isZero();
        JsonNode page = ok("/api/architecture/portfolio?sort=name&page=1&size=2");
        assertThat(ids(page)).containsExactly("c", "d");
        assertThat(page.path("totalCount").asLong()).isEqualTo(5);
        assertThat(page.path("totalPages").asLong()).isEqualTo(3);
        assertThat(page.at("/summary/readinessCounts/READY").asLong()).isEqualTo(2);
        assertThat(page.at("/summary/readinessCounts/NOT_ASSESSED").asLong()).isEqualTo(1);
        assertThat(page.at("/summary/readinessCounts/ATTENTION_REQUIRED").asLong()).isEqualTo(1);
        assertThat(page.at("/summary/readinessCounts/BLOCKED").asLong()).isEqualTo(1);
        assertThat(page.at("/summary/passportMissingCount").asLong()).isEqualTo(1);
        assertThat(page.at("/summary/acceptedHighRiskCount").asLong()).isEqualTo(1);
        assertThat(ok("/api/architecture/portfolio?page=2147483647&size=100").path("items").size()).isZero();
        JsonNode defaults = ok("/api/architecture/portfolio");
        assertThat(defaults.path("page").asInt()).isZero(); assertThat(defaults.path("size").asInt()).isEqualTo(20);
    }

    @Test
    void everySortHasStableIdTieBreakAndUnknownValuesStayLastInBothDirections() throws Exception {
        portfolioFixtures();
        Map<String, List<String>> asc = Map.of(
            "name", List.of("a", "b", "c", "d", "e"), "owner", List.of("a", "c", "b", "d", "e"),
            "quarter", List.of("a", "b", "c", "d", "e"), "projectStatus", List.of("a", "c", "d", "b", "e"),
            "readiness", List.of("d", "b", "e", "a", "c"), "criticality", List.of("a", "b", "c", "d", "e"));
        Map<String, List<String>> desc = Map.of(
            "name", List.of("e", "d", "c", "a", "b"), "owner", List.of("d", "b", "a", "c", "e"),
            "quarter", List.of("e", "d", "a", "b", "c"), "projectStatus", List.of("e", "b", "a", "c", "d"),
            "readiness", List.of("a", "c", "e", "b", "d"), "criticality", List.of("d", "c", "b", "a", "e"));
        for (String sort : asc.keySet()) {
            assertThat(ids(ok("/api/architecture/portfolio?sort=" + sort))).as(sort + " asc").isEqualTo(asc.get(sort));
            assertThat(ids(ok("/api/architecture/portfolio?sort=" + sort + "&direction=desc"))).as(sort + " desc").isEqualTo(desc.get(sort));
        }
        JsonNode rows = ok("/api/architecture/portfolio");
        assertThat(rows.path("calculationDate").asText()).isEqualTo("2026-10-14"); // UTC still Oct 13
        assertThat(rows.path("timeZone").asText()).isEqualTo("Europe/Moscow");
        for (JsonNode item : rows.path("items")) {
            assertThat(item.at("/assessment/calculationDate")).isEqualTo(rows.path("calculationDate"));
            assertThat(item.at("/assessment/timeZone")).isEqualTo(rows.path("timeZone"));
            assertThat(item.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        }
        assessments.contextCalls.set(0); ok("/api/architecture/portfolio");
        assertThat(assessments.contextCalls).hasValue(1);
    }

    @Test
    void quarterAndProjectStatusNormalizeStoredAndRequestedValuesWithoutChangingRows() throws Exception {
        project("a", "Alpha", "Alice", "  q1  ", " AcTiVe "); ready("a", "HIGH");
        project("b", "Beta", "ALICE", " Q1 ", "  ACTIVE  "); ready("b", "HIGH");
        // Each decoy fails exactly one of the intersected filters.
        project("quarter", "Quarter", "Alice", " Q2 ", " active "); ready("quarter", "HIGH");
        project("status", "Status", "Alice", " q1 ", " paused "); ready("status", "HIGH");
        project("owner", "Owner", "Bob", " Q1 ", " ACTIVE "); ready("owner", "HIGH");
        project("criticality", "Criticality", "Alice", " q1 ", " active "); ready("criticality", "LOW");
        project("readiness", "Readiness", "Alice", " Q1 ", " active "); passport("readiness", "HIGH", "NOT_STARTED", false);
        var before = snapshot();
        for (int page = 0; page < 2; page++) {
            var response = mvc.perform(get("/api/architecture/portfolio")
                .param("query", " aLiCe ").param("quarter", " Q1 ").param("projectStatus", " aCtIvE ")
                .param("readiness", " READY ").param("criticality", " high ")
                .param("sort", "name").param("page", Integer.toString(page)).param("size", "1"))
                .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            JsonNode result = mapper.readTree(response.getContentAsString());
            assertThat(ids(result)).containsExactly(page == 0 ? "a" : "b");
            assertThat(result.path("page").asInt()).isEqualTo(page);
            assertThat(result.path("size").asInt()).isEqualTo(1);
            assertThat(result.path("totalCount").asLong()).isEqualTo(2);
            assertThat(result.path("totalPages").asLong()).isEqualTo(2);
            assertThat(result.at("/summary/readinessCounts/READY").asLong()).isEqualTo(2);
            for (String other : List.of("BLOCKED", "ATTENTION_REQUIRED", "NOT_ASSESSED"))
                assertThat(result.at("/summary/readinessCounts/" + other).asLong()).isZero();
            assertThat(result.at("/summary/passportPresentCount").asLong()).isEqualTo(2);
            assertThat(result.at("/summary/passportMissingCount").asLong()).isZero();
            assertThat(result.at("/summary/acceptedHighRiskCount").asLong()).isZero();
            // Response DTOs also retain the original persisted spelling and spaces.
            assertThat(result.at("/items/0/project/quarter").asText()).isEqualTo(page == 0 ? "  q1  " : " Q1 ");
            assertThat(result.at("/items/0/project/projectStatus").asText()).isEqualTo(page == 0 ? " AcTiVe " : "  ACTIVE  ");
        }
        assertThat(ids(ok("/api/architecture/portfolio?query=alice&quarter=q1&projectStatus=active&readiness=READY&criticality=HIGH")))
            .containsExactly("a", "b");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void malformedUnknownAndDuplicateParametersReturnSafe400() throws Exception {
        for (String parameter : List.of("projectStatus=unknown", "readiness=unknown", "criticality=unknown", "quarter=unknown",
            "sort=java.lang.Exception", "direction=sideways", "page=-1", "page=999999999999999999", "size=0", "size=101",
            "size=abc", "unknown=secret", "page=0&page=1", "quarter=", "readiness=")) {
            JsonNode error = request("/api/architecture/portfolio?" + parameter, 400);
            assertThat(error.path("code").asText()).isEqualTo("BAD_REQUEST");
            assertThat(error.path("error").asText()).isEqualTo("Invalid architecture portfolio parameters");
            assertThat(error.toString()).doesNotContain("java.lang", "secret", "SQLException", "stackTrace", "C:\\");
        }
    }

    @Test
    void bothImpactsCountSeparatelyAndPreserveLegacyFields() throws Exception {
        project("full", "Full", "Owner", "Q1", "active"); ready("full", "HIGH"); details("full");
        oldChildren("full");
        JsonNode impact = ok("/api/projects/full/architecture/deletion-impact");
        assertThat(impact.path("passportPresent").asBoolean()).isTrue();
        for (String field : List.of("componentCount", "integrationCount", "securityRiskCount", "environmentCount"))
            assertThat(impact.path(field).asLong()).as(field).isEqualTo(1);
        assertThat(impact.path("checkCount").asLong()).isEqualTo(10);
        assertThat(impact.path("dataClassCount").asLong()).isEqualTo(2);
        assertThat(impact.path("deletionAllowed").asBoolean()).isFalse();
        assertThat(impact.path("blockers").size()).isEqualTo(6);
        JsonNode legacy = ok("/api/projects/full/deletion-impact");
        assertThat(legacy.path("id").asText()).isEqualTo("full");
        assertThat(legacy.path("name").asText()).isEqualTo("Full");
        assertThat(legacy.path("taskCount").asLong()).isEqualTo(1); assertThat(legacy.path("milestoneCount").asLong()).isEqualTo(1);
        assertThat(legacy.path("architecturePassportCount").asLong()).isEqualTo(1);
        assertThat(legacy.path("architectureImpactPreliminary").asBoolean()).isFalse();
        for (String field : List.of("componentCount", "integrationCount", "checkCount", "securityRiskCount"))
            assertThat(legacy.path(field)).isEqualTo(impact.path(field));
        project("bare", "Bare", "Owner", "Q1", "active"); passport("bare", "LOW", "NOT_STARTED", false);
        assertThat(ok("/api/projects/bare/architecture/deletion-impact").path("deletionAllowed").asBoolean()).isTrue();
    }

    @Test
    void repeatGetsKeepAllRowsDatesAndVersionsAndUseReadOnlyRepeatableReadWithoutWriteLocks() throws Exception {
        project("full", "Full", "Owner", "Q1", "active"); ready("full", "HIGH"); details("full");
        oldChildren("full");
        project("empty", "Empty", "Owner", "Q1", "planned");
        var before = snapshot();
        inspector.checked = true;
        for (int repeat = 0; repeat < 3; repeat++) {
            ok("/api/projects/full/architecture"); ok("/api/architecture/portfolio");
            ok("/api/projects/full/architecture/deletion-impact"); ok("/api/projects/full/deletion-impact");
            ok("/api/projects/empty/architecture");
        }
        inspector.checked = false;
        assertThat(inspector.statements).isNotEmpty().allSatisfy(sql ->
            assertThat(sql.toLowerCase()).startsWith("select ").doesNotContain("for update", " for share"));
        assertThat(snapshot()).isEqualTo(before);
        // Existing PM-001/PM-002 routes retain their contracts; full suite covers their mutations.
        assertThat(ok("/api/projects/full").path("id").asText()).isEqualTo("full");
        ok("/api/projects"); ok("/api/portfolio"); ok("/api/projects/full/board");
    }

    @Test
    void databaseFailuresHaveSafeStructuredResponsesAndLeaveRowsUntouched() throws Exception {
        project("p", "Project", "Owner", "Q1", "active"); ready("p", "LOW");
        var before = snapshot();
        inspector.fail = true;
        try {
            for (String path : List.of("/api/projects/p/architecture", "/api/architecture/portfolio",
                "/api/projects/p/architecture/deletion-impact", "/api/projects/p/deletion-impact")) {
                JsonNode error = request(path, 500);
                assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR");
                assertThat(error.path("error").asText()).isEqualTo("Database operation failed");
                assertThat(error.toString()).doesNotContain("SELECT", "secret", "java.sql", "stackTrace", "C:\\");
            }
        } finally { inspector.fail = false; }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void queryCountGrowsByBatchNotByProjectAndNoLazyEntitiesAreFetched() throws Exception {
        project("p0", "Project", "Owner", "Q1", "active"); ready("p0", "LOW"); details("p0");
        var stats = emf.unwrap(SessionFactoryImplementor.class).getStatistics();
        stats.clear(); ok("/api/architecture/portfolio"); long single = stats.getPrepareStatementCount();
        assertThat(single).isEqualTo(7);
        for (int i = 1; i < 31; i++) { project("p" + i, "Project", "Owner", "Q1", "active"); ready("p" + i, "LOW"); details("p" + i); }
        stats.clear(); ok("/api/architecture/portfolio");
        assertThat(stats.getPrepareStatementCount()).isEqualTo(single);
        assertThat(stats.getEntityLoadCount()).isZero(); assertThat(stats.getEntityFetchCount()).isZero();
        assertThat(stats.getCollectionFetchCount()).isZero();
        for (int i = 31; i < 501; i++) project("p" + i, "Project", "Owner", "Q1", "active");
        stats.clear(); JsonNode page = ok("/api/architecture/portfolio?size=1");
        assertThat(page.path("totalCount").asLong()).isEqualTo(501);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(13);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentCommittedChangesCannotMixRowsInsideOneReadSnapshot(boolean portfolio) throws Exception {
        project("p", "Old name", "Owner", "Q1", "active"); ready("p", "LOW");
        inspector.reached = new CountDownLatch(1); inspector.resume = new CountDownLatch(1); inspector.pause = true;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = executor.submit(() -> ok(portfolio ? "/api/architecture/portfolio" : "/api/projects/p/architecture"));
            assertTrue(inspector.reached.await(10, TimeUnit.SECONDS), "GET must reach child query while its transaction is open");
            // A second connection commits after GET read the project and passport, before its check query.
            jdbc.update("update projects set name='New name',version=version+1 where id='p'");
            jdbc.update("update project_architecture_checks set status='BLOCKED',version=version+1 where project_id='p'");
            inspector.resume.countDown();
            JsonNode result = response.get(10, TimeUnit.SECONDS);
            JsonNode old = portfolio ? result.at("/items/0") : result;
            assertThat(old.at("/project/name").asText()).isEqualTo("Old name");
            assertThat(old.at("/assessment/status").asText()).isEqualTo("READY");
            if (!portfolio) assertThat(old.path("checks").findValuesAsText("status")).containsOnly("PASSED");
        } finally { inspector.pause = false; inspector.resume.countDown(); }
        JsonNode current = ok("/api/projects/p/architecture");
        assertThat(current.at("/project/name").asText()).isEqualTo("New name");
        assertThat(current.at("/assessment/status").asText()).isEqualTo("BLOCKED");
    }

    private JsonNode request(String path, int status) throws Exception {
        var result = mvc.perform(get(path)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path + " " + result.getResponse().getContentAsString()).isEqualTo(status);
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    private JsonNode ok(String path) throws Exception { return request(path, 200); }
    private static List<String> ids(JsonNode portfolio) {
        List<String> result = new ArrayList<>(); portfolio.path("items").forEach(item -> result.add(item.at("/project/id").asText())); return result;
    }
    private static List<String> codes(JsonNode assessment) { return assessment.path("reasons").findValuesAsText("code"); }
    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForList("select * from " + table).stream()
            .sorted(java.util.Comparator.comparing(Object::toString)).toList());
        return result;
    }
    private void project(String id, String name, String owner, String quarter, String status) {
        jdbc.update("insert into projects (id,name,owner,status,health,quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,budget,progress,version) "
            + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id,name,owner,status,"green",quarter,"","","","","","","hidden-summary",2147483647,0,7);
    }
    private void passport(String id, String criticality, String stage, boolean complete) {
        jdbc.update("insert into project_architecture_passports (instance_id,project_id,criticality,stage,solution_type,architect,solution_summary,availability_requirement,recovery_requirement,last_review_date,version,created_at,updated_at) "
            + "values (random_uuid(),?,?,?,?,?,?,?,?,?,?,?,?)", id,criticality,stage,"NEW_SOLUTION",complete ? "Architect" : null,complete ? "Solution" : null,
            complete ? "99.9%" : null,complete ? "1 hour" : null,complete ? "2026-10-01" : null,8,
            "2026-01-01T00:00:00Z","2026-02-01T00:00:00Z");
    }
    private void ready(String id, String criticality) {
        passport(id,criticality,"APPROVED",true);
        jdbc.update("insert into project_architecture_data_classes (project_id,class_code) values (?,?)",id,"PUBLIC");
        jdbc.update("insert into project_architecture_data_classes (project_id,class_code) values (?,?)",id,"INTERNAL");
        jdbc.update("insert into project_architecture_environments (project_id,environment_code) values (?,?)",id,"PROD");
        int index = 0;
        for (String type : ArchitectureReadinessAssessmentService.CHECK_TYPES)
            jdbc.update("insert into project_architecture_checks (id,project_id,check_type,required,status,actual_date,version) values (?,?,?,?,?,?,?)",
                id + "-check-" + index++,id,type,true,"PASSED","2026-10-01",11);
    }
    private void details(String id) {
        jdbc.update("insert into project_architecture_components (id,project_id,name,component_type,criticality,external_component,position,version) values (?,?,?,?,?,?,?,?)",
            id+"-component",id,"Backend","BACKEND","HIGH",false,0,9);
        jdbc.update("insert into project_architecture_integrations (id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values (?,?,?,?,?,?,?,?,?,?,?)",
            id+"-integration",id,"Exchange","Source","Target","OUTBOUND","API","OAUTH2","HIGH","READY",10);
        jdbc.update("insert into project_integration_data_classes (integration_id,class_code) values (?,?)",id+"-integration","PUBLIC");
        jdbc.update("insert into project_integration_data_classes (integration_id,class_code) values (?,?)",id+"-integration","PERSONAL");
        risk(id+"-risk",id,"LOW","CRITICAL","ACCEPTED");
    }
    private void risk(String riskId, String projectId, String probability, String impact, String status) {
        jdbc.update("insert into project_security_risks (id,project_id,name,category,probability,impact,status,acceptance_justification,version) values (?,?,?,?,?,?,?,?,?)",
            riskId,projectId,"Risk","DATA",probability,impact,status,"ACCEPTED".equals(status) ? "Business acceptance" : null,12);
    }
    private void oldChildren(String id) {
        jdbc.update("insert into board_cards (id,project_id,column_key,position,title,description,owner,due_date,estimate,blocked) values (?,?,?,?,?,?,?,?,?,?)",
            id + "-card",id,"backlog",0,"Task","Description","Owner","",0,false);
        jdbc.update("insert into project_milestones (id,project_id,name,planned_date,completed,completed_date,position) values (?,?,?,?,?,?,?)",
            id + "-milestone",id,"Gate","2026-10-01",true,"2026-10-01",0);
        jdbc.update("insert into release_trains (id,name,status,cadence,quarter,planned_release_date,code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) "
            + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id + "-train","Train","planned","monthly","Q1","","","","",0,0,0,0,"Scope","Risk","Decision");
    }
    private void portfolioFixtures() {
        project("a","Alpha","Alice","Q1","active"); ready("a","LOW");
        project("b","Alpha","Bob","Q1","paused"); ready("b","MEDIUM"); risk("br","b","LOW","CRITICAL","ACCEPTED");
        project("c","Beta","ALICE","Q1","active"); ready("c","HIGH");
        project("d","Delta","Carol","Q2","active"); ready("d","CRITICAL"); risk("dr","d","CRITICAL","CRITICAL","OPEN");
        project("e","Echo","","Q3","planned");
    }

    static class CountingAssessment extends ArchitectureReadinessAssessmentService {
        final AtomicInteger contextCalls = new AtomicInteger();
        CountingAssessment() { super(Clock.fixed(Instant.parse("2026-10-13T21:05:00Z"), ZoneOffset.UTC)); }
        @Override public project_manager.domain.ArchitectureAssessmentContext newContext() { contextCalls.incrementAndGet(); return super.newContext(); }
    }
    static class ReadInspector implements StatementInspector {
        private final DataSource dataSource;
        volatile boolean checked, pause, fail;
        volatile CountDownLatch reached, resume;
        final List<String> statements = new CopyOnWriteArrayList<>();
        ReadInspector(DataSource dataSource) { this.dataSource = dataSource; }
        @Override public String inspect(String sql) {
            if (fail) throw new org.springframework.dao.DataAccessResourceFailureException(
                "SELECT secret FROM passport; java.sql.SQLException at C:\\private\\db");
            if (checked) {
                statements.add(sql);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                Connection connection = DataSourceUtils.getConnection(dataSource);
                try { assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ); }
                catch (java.sql.SQLException error) { throw new AssertionError(error); }
                finally { DataSourceUtils.releaseConnection(connection, dataSource); }
            }
            if (pause && sql.contains(" from project_architecture_checks ")) {
                reached.countDown();
                try { assertTrue(resume.await(10, TimeUnit.SECONDS), "writer must release GET"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            }
            return sql;
        }
    }
    @TestConfiguration
    static class Configuration {
        @Bean @Primary CountingAssessment fixedAssessment() { return new CountingAssessment(); }
        @Bean ReadInspector readInspector(DataSource ds) { return new ReadInspector(ds); }
        @Bean HibernatePropertiesCustomizer inspection(ReadInspector inspector) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", inspector);
        }
    }
}
