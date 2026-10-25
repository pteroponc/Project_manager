package project_manager.safety;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import project_manager.ProjectManagerApplication;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static project_manager.safety.ArchitectureSchemaMigrationIntegrationTest.*;

/** Real controller/service/SQL on V002-V005 memory schema; Hibernate validates, never creates it. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pmtest_d1_summary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never", "spring.h2.console.enabled=false"})
@ActiveProfiles("smoke")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = ArchitecturePortfolioSummaryIntegrationTest.Schema.class)
class ArchitecturePortfolioSummaryIntegrationTest {
    private static final String ENDPOINT = "/api/architecture/portfolio/summary";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Inspector inspector;
    @Autowired Assessment assessment;

    static class Schema implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            // Validate the actual environment before the first manual connection, not a late datasource override.
            new TestDatabaseGuard().postProcessEnvironment(context.getEnvironment(), new SpringApplication(ProjectManagerApplication.class));
            String url=context.getEnvironment().getProperty("spring.datasource.url");
            try (var connection = setup(url, true)) {
                script(connection, "V004__architecture_security.sql");
                script(connection, "V005__architecture_passport_instance.sql");
            } catch (Exception e) { throw new IllegalStateException("Cannot prepare isolated D1 test schema",e); }
        }
    }

    @BeforeEach
    void reset() {
        inspector.active = false; inspector.sql.clear(); assessment.contexts.set(0);
        for (String table : List.of("project_integration_data_classes", "project_architecture_components",
            "project_architecture_integrations", "project_architecture_checks", "project_security_risks",
            "project_architecture_data_classes", "project_architecture_environments", "project_architecture_passports",
            "project_milestones", "board_cards", "release_trains", "projects")) jdbc.update("delete from " + table);
    }

    @Test
    void allProjectsFieldsNullsCountsAndNarrowAssessmentMatchCards() throws Exception {
        fixtures();
        component("a", "c1"); component("a", "c2");
        jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values ('i','a','Exchange','Source','Target','OUTBOUND','API','NONE','HIGH','READY',3)");
        jdbc.update("insert into project_integration_data_classes values ('i','PUBLIC')");
        JsonNode result = ok(ENDPOINT);
        assertThat(ids(result)).containsExactly("a", "b", "c", "e", "d");
        assertThat(assessment.contexts.get()).isEqualTo(1);
        assertThat(result.path("calculationDate").asText()).isEqualTo("2026-10-14");
        assertThat(result.path("timeZone").asText()).isEqualTo("Europe/Moscow");
        assertThat(result.path("page").asInt()).isZero();
        assertThat(result.path("size").asInt()).isEqualTo(20);
        assertThat(result.path("totalPages").asInt()).isEqualTo(1);
        assertThat(result.path("pageItemCount").asInt()).isEqualTo(5);
        for (JsonNode item : result.path("items")) {
            JsonNode card = ok("/api/projects/" + item.at("/project/id").asText() + "/architecture");
            assertThat(item.at("/assessment/status")).isEqualTo(card.at("/assessment/status"));
            assertThat(item.at("/assessment/statusLabel")).isEqualTo(card.at("/assessment/statusLabel"));
            assertThat(item.at("/assessment/reasonCount").asInt()).isEqualTo(card.at("/assessment/reasons").size());
            assertThat(item.at("/assessment/upcomingCheckCount").asInt()).isEqualTo(card.at("/assessment/upcomingChecks").size());
            assertThat(item.at("/assessment/acceptedHighRiskCount").asInt()).isEqualTo(card.at("/assessment/acceptedHighRisks").size());
            assertThat(item.path("project").size()).isEqualTo(7);
            assertThat(item.at("/project/name")).isEqualTo(card.at("/project/name"));
            assertThat(item.path("componentCount").asInt()).isEqualTo(card.path("components").size());
            assertThat(item.path("integrationCount").asInt()).isEqualTo(card.path("integrations").size());
            assertThat(item.path("checkCount").asInt()).isEqualTo(card.path("checks").size());
            assertThat(item.path("securityRiskCount").asInt()).isEqualTo(card.path("risks").size());
            assertThat(item.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        }
        JsonNode missing = item(result, "d");
        assertThat(missing.path("passportPresent").asBoolean()).isFalse();
        assertThat(missing.path("architect").isNull()).isTrue();
        assertThat(missing.path("documentVersion").isNull()).isTrue();
        assertThat(missing.at("/assessment/status").asText()).isEqualTo("NOT_ASSESSED");
        assertThat(missing.at("/assessment/reasonCount").asInt()).isPositive();
        assertThat(item(result,"a").path("architect").asText()).isEqualTo("Architect");
        assertThat(item(result,"a").path("documentVersion").asText()).isEqualTo("1.0");
        assertThat(item(result,"e").path("passportPresent").asBoolean()).isTrue();
        assertThat(item(result,"e").path("architect").isNull()).isTrue();
        assertThat(item(result,"c").at("/assessment/acceptedHighRiskCount").asInt()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("budget", "instanceId", "solutionSummary",
            "Business acceptance", "acceptanceJustification", "justification", "entityId",
            "upcomingChecks", "acceptedHighRisks", "reasons", "first-integration", "blocked");
        assertThat(result.findValues("version")).isEmpty();
        example("all", result);
    }

    @Test
    void filtersIntersectAndReadinessCountsExcludeOnlyTheReadinessFilter() throws Exception {
        fixtures();
        JsonNode all = ok(ENDPOINT);
        assertThat(all.path("totalCount").asInt()).isEqualTo(5);
        assertThat(all.path("readinessCounts").toString()).isEqualTo("{\"BLOCKED\":1,\"ATTENTION_REQUIRED\":1,\"NOT_ASSESSED\":2,\"READY\":1}");
        for (String state : List.of("BLOCKED", "ATTENTION_REQUIRED", "NOT_ASSESSED", "READY")) {
            JsonNode result = ok(ENDPOINT + "?readiness=" + state);
            assertThat(result.path("readinessCounts")).isEqualTo(all.path("readinessCounts"));
            assertThat(result.path("totalCount").asInt()).isEqualTo(5);
            assertThat(result.path("filteredCount").asInt()).isEqualTo(state.equals("NOT_ASSESSED") ? 2 : 1);
            assertThat(result.path("pageItemCount").asInt()).isEqualTo(result.path("filteredCount").asInt());
            result.path("items").forEach(i -> assertThat(i.at("/assessment/status").asText()).isEqualTo(state));
        }
        assertThat(ids(ok(ENDPOINT + "?query=%20ALPHA%20"))).containsExactly("a","b");
        assertThat(ids(ok(ENDPOINT + "?quarter=q1"))).containsExactly("a","b","c");
        assertThat(ids(ok(ENDPOINT + "?status=ACTIVE"))).containsExactly("a","b","d");
        JsonNode filtered = ok(ENDPOINT + "?query=alpha&quarter=q1&status=ACTIVE&readiness=READY");
        assertThat(ids(filtered)).containsExactly("a");
        assertThat(filtered.path("totalCount").asInt()).isEqualTo(2);
        assertThat(filtered.path("filteredCount").asInt()).isEqualTo(1);
        assertThat(filtered.at("/readinessCounts/BLOCKED").asInt()).isEqualTo(1);
        assertThat(filtered.at("/readinessCounts/READY").asInt()).isEqualTo(1);
        assertThat(filtered.at("/readinessCounts/NOT_ASSESSED").asInt()).isZero();
        assertThat(ok(ENDPOINT + "?query=alpha&readiness=NOT_ASSESSED").path("filteredCount").asInt()).isZero();
        example("filtered", filtered);
    }

    @Test
    void commonFiltersAgreeWithRegistryIncludingSummaryLiteralSearchAndLegacyValues() throws Exception {
        fixtures();
        jdbc.update("update projects set summary='Needle %_ summary' where id='c'");
        project("legacy", "Legacy", "Needle owner only", " Q9 ", " legacy ");
        for (String parameters : List.of("query=needle", "query=%25_", "query=%20ALPHA%20", "quarter=q1&status=ACTIVE",
            "quarter=%20Q9%20&status=%20legacy%20", "quarter=Q9", "status=legacy", "quarter=unknown", "status=unknown",
            "query=%27%20OR%201%3D1%20--")) {
            var actual = ids(ok(ENDPOINT + "?" + parameters));
            var registry = ok("/api/projects/registry?" + parameters);
            List<String> expected = new ArrayList<>(); registry.path("items").forEach(i -> expected.add(i.path("id").asText()));
            assertThat(actual).as(parameters).isEqualTo(expected);
        }
        assertThat(ids(ok(ENDPOINT + "?query=needle"))).containsExactly("c"); // Not owner search.
    }

    @Test
    void emptySelectionAndStableNameIdOrdering() throws Exception {
        JsonNode empty = ok(ENDPOINT);
        assertThat(ids(empty)).isEmpty();
        assertThat(empty.path("totalCount").asInt()).isZero();
        assertThat(empty.path("pageItemCount").asInt()).isZero();
        assertThat(empty.path("totalPages").asInt()).isZero();
        assertThat(empty.path("readinessCounts").size()).isEqualTo(4);
        empty.path("readinessCounts").forEach(c -> assertThat(c.asInt()).isZero());
        for (String id : List.of("z","a","A")) project(id,"  Alpha ","Owner","Q1","active");
        assertThat(ids(ok(ENDPOINT))).containsExactly("A","a","z");
        assertThat(ok(ENDPOINT)).isEqualTo(ok(ENDPOINT));
        assertThat(ids(ok(ENDPOINT + "?quarter=missing"))).isEmpty();
    }

    @Test
    void pagesHaveStableBoundariesAndFacetCountsRemainBeforeReadiness() throws Exception {
        fixtures();
        for (int page=0;page<4;page++) {
            JsonNode result=ok(ENDPOINT + "?size=2&page=" + page);
            assertThat(result.path("totalCount").asInt()).isEqualTo(5);
            assertThat(result.path("filteredCount").asInt()).isEqualTo(5);
            assertThat(result.path("page").asInt()).isEqualTo(page);
            assertThat(result.path("size").asInt()).isEqualTo(2);
            assertThat(result.path("totalPages").asInt()).isEqualTo(3);
            assertThat(result.path("pageItemCount").asInt()).isEqualTo(result.path("items").size());
            assertThat(ids(result)).isEqualTo(switch(page) {
                case 0 -> List.of("a","b"); case 1 -> List.of("c","e");
                case 2 -> List.of("d"); default -> List.of();
            });
        }
        JsonNode filtered=ok(ENDPOINT + "?size=1&page=1&readiness=NOT_ASSESSED");
        assertThat(ids(filtered)).containsExactly("d");
        assertThat(filtered.path("totalCount").asInt()).isEqualTo(5);
        assertThat(filtered.path("filteredCount").asInt()).isEqualTo(2);
        assertThat(filtered.path("totalPages").asInt()).isEqualTo(2);
        assertThat(filtered.at("/readinessCounts/READY").asInt()).isEqualTo(1);
        assertThat(ids(ok(ENDPOINT + "?size=1&page=2&readiness=NOT_ASSESSED"))).isEmpty();
        example("page", filtered);
    }

    @Test
    void sortKeysDirectionsAndProjectIdTieBreakStayStableAcrossPages() throws Exception {
        fixtures();
        jdbc.update("update projects set health='red',deadline='2026-10-01' where id='a'");
        jdbc.update("update projects set health='yellow',deadline='invalid' where id='b'");
        jdbc.update("update projects set deadline='2026-09-01' where id='c'");
        jdbc.update("update projects set health='legacy',deadline='2026-12-01' where id='d'");
        jdbc.update("update projects set deadline='2026-12-01' where id='e'");
        assertThat(ids(ok(ENDPOINT + "?sort=name&direction=desc"))).containsExactly("d","e","c","a","b");
        assertThat(ids(ok(ENDPOINT + "?sort=deadline"))).containsExactly("c","a","d","e","b");
        assertThat(ids(ok(ENDPOINT + "?sort=deadline&direction=desc"))).containsExactly("d","e","a","c","b");
        assertThat(ids(ok(ENDPOINT + "?sort=health"))).containsExactly("a","b","c","e","d");
        assertThat(ids(ok(ENDPOINT + "?sort=health&direction=desc"))).containsExactly("c","e","b","a","d");
        jdbc.update("update projects set health=' red ' where id='d'");
        assertThat(ids(ok(ENDPOINT + "?sort=health"))).containsExactly("a","b","c","e","d");
        assertThat(ids(ok(ENDPOINT + "?sort=owner&direction=desc"))).containsExactly("a","b","c","d","e");
        assertThat(ids(ok(ENDPOINT + "?sort=quarter&direction=desc"))).containsExactly("d","e","a","b","c");
        assertThat(ids(ok(ENDPOINT + "?sort=projectStatus"))).containsExactly("a","b","d","e","c");
        assertThat(ids(ok(ENDPOINT + "?sort=readiness"))).containsExactly("b","c","d","e","a");
        assertThat(ids(ok(ENDPOINT + "?sort=readiness&direction=desc"))).containsExactly("a","d","e","c","b");
        assertThat(ids(ok(ENDPOINT + "?sort=READINESS&direction=DESC&size=2&page=1"))).containsExactly("e","c");
        assertThat(ok(ENDPOINT + "?sort=readiness&size=2&page=1"))
            .isEqualTo(ok(ENDPOINT + "?sort=readiness&size=2&page=1"));
    }

    @Test
    void rejectsUnknownRepeatedAndInvalidReadinessParametersSafelyWithoutMutation() throws Exception {
        fixtures(); var before = rows();
        for (String parameters : List.of("unknown=private", "query=a&query=b", "status=a&status=b", "quarter=a&quarter=b",
            "readiness=READY&readiness=BLOCKED", "readiness=private", "readiness=", "page=-1", "page=abc",
            "page=2147483648", "size=0", "size=101", "size=x", "sort=private",
            "direction=sideways", "page=1&page=2", "sort=name&sort=owner", "size=2&size=3")) {
            JsonNode error = request(ENDPOINT + "?" + parameters,400);
            assertThat(error.path("code").asText()).isEqualTo("BAD_REQUEST");
            assertThat(error.toString()).doesNotContain("private", "java.", "SQL", "stack", "C:\\");
        }
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void repeatedGetsPreserveAllTablesDatesVersionsAndLegacyApiContracts() throws Exception {
        fixtures(); component("a","old-component");
        jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) values ('old-card','a','custom',47,'Task','Description','Owner','')");
        jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,position) values ('old-point','a','Gate','2026-10-01',false,0)");
        var before = rows();
        for (int i=0;i<3;i++) { ok(ENDPOINT); ok(ENDPOINT + "?readiness=READY"); ok("/api/projects/d/architecture"); }
        JsonNode legacy = ok("/api/architecture/portfolio?query=Owner&readiness=READY");
        assertThat(legacy.path("items").size()).isEqualTo(1);
        assertThat(legacy.at("/summary/readinessCounts/READY").asInt()).isEqualTo(1);
        assertThat(legacy.at("/summary/readinessCounts/BLOCKED").asInt()).isZero();
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void sqlCountIsConstantEvenBeyondOldBatchBoundaryAndReadTransactionHasNoWriteLocks() throws Exception {
        project("p0","Project","Owner","Q1","active");
        passport("p0",true); component("p0","first-component");
        jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness) values ('first-integration','p0','Exchange','Source','Target','OUTBOUND','API','NONE','HIGH','READY')");
        jdbc.update("insert into project_integration_data_classes values ('first-integration','PUBLIC')");
        jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status) values ('first-risk','p0','Risk','DATA','LOW','LOW','OPEN')");
        assertThat(sqlCount()).isEqualTo(8);
        for (int i=1;i<30;i++) {
            project("p"+i,"Project "+i,"Owner","Q1","active");
            passport("p"+i,false); component("p"+i,"component"+i);
        }
        assertThat(sqlCount()).isEqualTo(8);
        for (int i=30;i<501;i++) project("p"+i,"Project "+i,"Owner","Q1","active");
        assertThat(sqlCount()).isEqualTo(8); // No new SELECTs at the former 500-ID batch boundary.
        assertThat(sqlCount("?size=20&page=25")).isEqualTo(8);
        JsonNode tail=ok(ENDPOINT + "?size=20&page=25");
        assertThat(tail.path("filteredCount").asInt()).isEqualTo(501);
        assertThat(tail.path("pageItemCount").asInt()).isEqualTo(1);
        assertThat(tail.path("totalPages").asInt()).isEqualTo(26);
        assertThat(sqlCount("?query=missing")).isEqualTo(1);
    }

    private int sqlCount() throws Exception { return sqlCount(""); }
    private int sqlCount(String parameters) throws Exception {
        inspector.sql.clear(); inspector.active=true;
        try { ok(ENDPOINT + parameters); } finally { inspector.active=false; }
        assertThat(inspector.sql).allSatisfy(sql -> {
            assertThat(sql.toLowerCase()).startsWith("select ").doesNotContain("for update");
            assertThat(sql.toLowerCase()).doesNotContain("source_system", "target_system", "treatment_plan",
                "closed_date", "created_at", "updated_at", "auth_category", "platform", "purpose");
        });
        return inspector.sql.size();
    }
    private JsonNode request(String url, int status) throws Exception {
        var response = mvc.perform(get(java.net.URI.create(url))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return json.readTree(response.getContentAsString());
    }
    private JsonNode ok(String url) throws Exception { return request(url,200); }
    private void example(String suffix, JsonNode response) throws Exception {
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("tmp"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("tmp", "PM-003D.1-example-" + suffix + ".json"),
            json.writerWithDefaultPrettyPrinter().writeValueAsString(response) + "\n");
    }
    private static List<String> ids(JsonNode response) {
        List<String> ids=new ArrayList<>(); response.path("items").forEach(i -> ids.add(i.at("/project/id").asText())); return ids;
    }
    private static JsonNode item(JsonNode response,String id) {
        for (JsonNode i:response.path("items")) if (id.equals(i.at("/project/id").asText())) return i;
        throw new AssertionError("Missing project " + id);
    }
    private Map<String,List<Map<String,Object>>> rows() {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for (String table:jdbc.queryForList("select table_name from information_schema.tables where table_schema='PUBLIC' order by table_name",String.class))
            result.put(table,jdbc.queryForList("select * from " + table + " order by 1,2"));
        return result;
    }
    private void project(String id,String name,String owner,String quarter,String status) {
        jdbc.update("insert into projects(id,name,owner,status,health,quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,budget,progress,version) values (?,?,?,?,? ,?,?,?,?,?,?,?,?,?,?,?)",
            id,name,owner,status,"green",quarter,"","","","","","","Description",Integer.MAX_VALUE,0,7);
    }
    private void passport(String id, boolean complete) {
        jdbc.update("insert into project_architecture_passports(project_id,instance_id,criticality,stage,solution_type,architect,solution_summary,availability_requirement,recovery_requirement,last_review_date,version,created_at,updated_at) values (?,random_uuid(),'HIGH',?,'NEW_SOLUTION',?,?,?,?,?,8,'2026-01-01T00:00:00Z','2026-02-01T00:00:00Z')",
            id,complete?"APPROVED":"NOT_STARTED",complete?"Architect":null,complete?"Solution":null,complete?"Availability":null,complete?"Recovery":null,complete?"2026-10-01":null);
        if (complete) {
            jdbc.update("insert into project_architecture_data_classes values (?,'PUBLIC')",id);
            jdbc.update("insert into project_architecture_environments values (?,'PROD')",id);
            for (String type:ArchitectureReadinessAssessmentService.CHECK_TYPES)
                jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,actual_date,version) values (?,?,?,true,'PASSED','2026-10-01',3)",id+type,id,type);
        }
    }
    private void component(String project,String id) {
        jdbc.update("insert into project_architecture_components(id,project_id,name,component_type,criticality,external_component,position,version) values (?,?,'Component','BACKEND','LOW',false,0,2)",id,project);
    }
    private void fixtures() {
        project("a","Alpha","Owner","Q1","active"); passport("a",true);
        project("b","Alpha","Owner","Q1","active"); passport("b",true);
        project("c","Beta","Owner","Q1","paused"); passport("c",true);
        project("d","No passport","Owner","Q2","active");
        project("e","Incomplete","Owner","Q2","done"); passport("e",false);
        jdbc.update("update projects set budget=0 where id='a'");
        jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,version) values ('blocked','b','Risk','DATA','CRITICAL','CRITICAL','OPEN',2)");
        jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,acceptance_justification,version) values ('accepted','c','Risk','DATA','LOW','CRITICAL','ACCEPTED','Business acceptance',2)");
    }
    static class Assessment extends ArchitectureReadinessAssessmentService {
        final AtomicInteger contexts=new AtomicInteger();
        Assessment() { super(Clock.fixed(Instant.parse("2026-10-13T21:05:00Z"),ZoneOffset.UTC)); }
        @Override public project_manager.domain.ArchitectureAssessmentContext newContext() { contexts.incrementAndGet(); return super.newContext(); }
    }
    static class Inspector implements StatementInspector {
        final DataSource datasource; final List<String> sql=new CopyOnWriteArrayList<>(); volatile boolean active;
        Inspector(DataSource datasource) { this.datasource=datasource; }
        @Override public String inspect(String statement) {
            if (active) {
                sql.add(statement);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                Connection connection=DataSourceUtils.getConnection(datasource);
                try { assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ); }
                catch (java.sql.SQLException e) { throw new AssertionError(e); }
                finally { DataSourceUtils.releaseConnection(connection,datasource); }
            }
            return statement;
        }
    }
    @TestConfiguration
    static class Config {
        @Bean @Primary Assessment assessment() { return new Assessment(); }
        @Bean Inspector inspector(DataSource source) { return new Inspector(source); }
        @Bean HibernatePropertiesCustomizer inspection(Inspector inspector) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector",inspector);
        }
    }
}
