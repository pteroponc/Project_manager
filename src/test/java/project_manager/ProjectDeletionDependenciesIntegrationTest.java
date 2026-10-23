package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import project_manager.repository.ArchitectureReadRepository;
import project_manager.service.ArchitecturePassportDeletionService;
import project_manager.service.ArchitectureReadinessAssessmentService;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pmtest_project_dependencies;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true"})
@org.springframework.test.context.ContextConfiguration(initializers=ProjectDeletionDependenciesIntegrationTest.MemorySchema.class)
@AutoConfigureMockMvc
class ProjectDeletionDependenciesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired project_manager.service.BoardService boards;
    @Autowired jakarta.persistence.EntityManagerFactory emf;
    @MockitoSpyBean project_manager.service.ProjectWriteLock locks;
    @MockitoSpyBean ArchitectureReadRepository readRepository;
    @TestBean(name = "architectureAssessmentClock", methodName = "fixedClock", enforceOverride = true) Clock clock;
    private static final String ROOT = "/api/projects/p";
    private static final List<String> TABLES = List.of("project_integration_data_classes", "project_architecture_components",
        "project_architecture_integrations", "project_architecture_checks", "project_security_risks", "project_architecture_data_classes",
        "project_architecture_environments", "project_architecture_passports", "project_milestones", "board_cards", "release_trains", "projects");
    static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"),ZoneOffset.UTC); }

    public static class MemorySchema implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {
        @Override public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
            String url=context.getEnvironment().getProperty("spring.datasource.url");
            if (url==null || !url.startsWith("jdbc:h2:mem:pmtest_")) throw new IllegalStateException("Memory tests only");
            try (var connection=java.sql.DriverManager.getConnection(url,"sa","")) {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("pm002a-old-schema.sql"));
                for (String file : List.of("V002__project_model_and_milestones.sql","V003__board_cards_project_fk.sql","V004__architecture_security.sql","V005__architecture_passport_instance.sql"))
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("db/manual/"+file));
            } catch (java.sql.SQLException error) { throw new IllegalStateException("Memory schema setup failed",error); }
        }
    }
    @BeforeEach void resetState() {
        reset(readRepository,locks);
        TABLES.forEach(table -> jdbc.update("delete from "+table));
        for (String id : List.of("p","q","r"))
            jdbc.update("insert into projects(id,name,owner,status,health,delivery_model,budget,progress,quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,version) values (?,?,'Owner','active','green','kanban',2147483647,0,'Q1','','','','','','','Summary',1)",id,id);
        for (String type : List.of("task","milestone","passport","component","integration","check","risk")) seed(type,"q");
        jdbc.update("insert into release_trains(id,name,status,cadence,quarter,planned_release_date,code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) values ('train','Train','planned','monthly','Q1','','','','',0,0,0,0,'Scope','Risk','Decision')");
    }
    @Test void emptyProjectReturns204AndOtherRowsArePreservedAndMissingIs404() throws Exception {
        var before=snapshot(); call(delete(ROOT),null,204);
        before.put("projects",before.get("projects").stream().filter(row -> !row.get("ID").equals("p")).toList());
        assertThat(snapshot()).isEqualTo(before); noOrphans();
        assertThat(call(delete(ROOT),null,404).path("code").asText()).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(call(get(ROOT+"/deletion-impact"),null,404).path("code").asText()).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<String> dependencyKinds() { return Stream.of("task","milestone","passport","component","integration","check","risk","legacy-mixed","all"); }
    @ParameterizedTest @MethodSource("dependencyKinds")
    void everyDependencyIsExplicitlyCountedAndGetMatchesDeleteWithoutAnyChanges(String type) throws Exception {
        if (type.equals("all")) for (String item : List.of("task","milestone","passport","component","integration","check","risk")) seed(item,"p");
        else if (type.equals("legacy-mixed")) { seed("task","p");seed("milestone","p"); }
        else seed(type,"p");
        var before=snapshot(); JsonNode impact=call(get(ROOT+"/deletion-impact"),null,200);
        assertThat(impact.path("deletionAllowed").asBoolean()).isFalse();
        assertThat(impact.path("architectureImpactPreliminary").asBoolean()).isFalse();
        Map<String,String> counts=Map.of("task","taskCount","milestone","milestoneCount","passport","architecturePassportCount","component","componentCount","integration","integrationCount","check","checkCount","risk","securityRiskCount");
        for (var item : counts.entrySet()) {
            boolean architecture=List.of("passport","component","integration","check","risk").contains(type);
            boolean expected=type.equals("all") || type.equals("legacy-mixed") && List.of("task","milestone").contains(item.getKey())
                || item.getKey().equals(type) || architecture && item.getKey().equals("passport");
            assertThat(impact.path(item.getValue()).asLong()).as(item.getValue()).isEqualTo(expected?1:0);
        }
        JsonNode error=call(delete(ROOT),null,409);
        assertThat(error.path("impact")).isEqualTo(impact);
        String expectedCode=type.equals("task")?"PROJECT_HAS_BOARD_CARDS":type.equals("milestone")?"PROJECT_HAS_MILESTONES":"PROJECT_HAS_DEPENDENCIES";
        assertThat(error.path("code").asText()).isEqualTo(expectedCode);
        assertThat(error.path("error").asText()).isEqualTo("Project has dependent records and cannot be deleted: p");
        assertThat(snapshot()).isEqualTo(before); noOrphans();
    }
    @Test void impactUsesSevenScalarCountsInReadOnlyRepeatableReadAndNoAggregateLoad() throws Exception {
        seed("task","p");seed("passport","p");var before=snapshot();
        doThrow(new AssertionError("Must not load architecture aggregate")).when(readRepository).load(anyList());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) java.sql.Connection::getTransactionIsolation)).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            return invocation.callRealMethod();
        }).when(readRepository).projectDeletionCounts("p");
        var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();
        call(get(ROOT+"/deletion-impact"),null,200);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(8); // Project row + seven COUNTs, independent of child volume.
        assertThat(stats.getEntityLoadCount()).isEqualTo(1);
        for (int i=0;i<3;i++) call(get(ROOT+"/deletion-impact"),null,200);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void v003MetadataAndConstraintsExistOnManuallyMigratedMemorySchema() {
        var fk=jdbc.queryForList("select r.delete_rule from information_schema.referential_constraints r join information_schema.table_constraints c on c.constraint_name=r.constraint_name and c.constraint_schema=r.constraint_schema where c.table_schema='PUBLIC' and c.table_name='BOARD_CARDS' and c.constraint_name='FK_BOARD_CARDS_PROJECT'");
        assertThat(fk).hasSize(1);assertThat(fk.get(0).get("DELETE_RULE")).isIn("NO ACTION","RESTRICT");
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema='PUBLIC' and table_name='BOARD_CARDS' and column_name='PROJECT_ID'",String.class)).isEqualTo("NO");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.index_columns where table_schema='PUBLIC' and table_name='BOARD_CARDS' and index_name='IDX_BOARD_CARDS_PROJECT' and column_name='PROJECT_ID' and ordinal_position=1",Long.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) values ('orphan','missing','backlog',0,'Task','','','')")).isInstanceOf(Exception.class);
        noOrphans();
    }
    static Stream<Arguments> createOrders() { return Stream.of("task","milestone","passport").flatMap(type -> Stream.of(Arguments.of(type,true),Arguments.of(type,false))); }
    @ParameterizedTest @MethodSource("createOrders")
    void createVersusDeleteInBothOrdersActuallyWaitsAndNeverMakesOrphansOr500(String type,boolean createFirst) throws Exception {
        CountDownLatch written=new CountDownLatch(1),release=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own=createFirst?unchecked(post(createPath(type)),createBody(type),type.equals("task")?200:201):unchecked(delete(ROOT),null,204);
                written.countDown();await(release);return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second=executor.submit(() -> createFirst?unchecked(delete(ROOT),null,409):unchecked(post(createPath(type)),createBody(type),404));
                blocked("projects",second);release.countDown();first.get(5,TimeUnit.SECONDS);
                JsonNode result=second.get(5,TimeUnit.SECONDS);
                if (createFirst) {
                    assertThat(result.path("code").asText()).isEqualTo(type.equals("task")?"PROJECT_HAS_BOARD_CARDS":type.equals("milestone")?"PROJECT_HAS_MILESTONES":"PROJECT_HAS_DEPENDENCIES");
                    assertThat(result.path("impact")).isEqualTo(call(get(ROOT+"/deletion-impact"),null,200));
                    assertThat(result.at("/impact/"+(type.equals("task")?"taskCount":type.equals("milestone")?"milestoneCount":"architecturePassportCount")).asLong()).isEqualTo(1);
                } else {
                    assertThat(result.path("code").asText()).isEqualTo("PROJECT_NOT_FOUND");
                    assertThat(jdbc.queryForObject("select count(*) from projects where id='p'",Long.class)).isZero();
                }
                noOrphans();
            } finally { release.countDown(); }
        }
    }
    @Test void twoDeletesOne204AndSecondWaitsThen404() throws Exception {
        CountDownLatch written=new CountDownLatch(1),release=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                unchecked(delete(ROOT),null,204);written.countDown();await(release);return true;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second=executor.submit(() -> unchecked(delete(ROOT),null,404));
                blocked("projects",second);release.countDown();first.get(5,TimeUnit.SECONDS);
                assertThat(second.get(5,TimeUnit.SECONDS).path("code").asText()).isEqualTo("PROJECT_NOT_FOUND");noOrphans();
            } finally { release.countDown(); }
        }
    }
    static Stream<Arguments> transferOrders() { return Stream.of("source","target").flatMap(side -> Stream.of(Arguments.of(side,true),Arguments.of(side,false))); }
    @ParameterizedTest @MethodSource("transferOrders")
    void transferVersusDeletionOfEitherProjectHoldsBothLocksAndPreservesData(String side,boolean transferFirst) throws Exception {
        String card=boards.createCard("p",cardRequest("Original")).id();
        String deleted=side.equals("source")?"p":"r";
        int deletionStatus=side.equals("source")&&!transferFirst || side.equals("target")&&transferFirst?409:204;
        CountDownLatch written=new CountDownLatch(1),release=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                if (transferFirst) boards.transferCard("p","r",card,cardRequest("Moved"));
                else { unchecked(delete("/api/projects/"+deleted),null,deletionStatus);if (deletionStatus==409) status.setRollbackOnly(); }
                written.countDown();await(release);return true;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second=executor.submit(() -> {
                    if (transferFirst) return (Object)unchecked(delete("/api/projects/"+deleted),null,deletionStatus);
                    try { return (Object)boards.transferCard("p","r",card,cardRequest("Moved")); }
                    catch (project_manager.web.ProjectMutationException error) { return error; }
                });
                blocked("projects",second);release.countDown();first.get(5,TimeUnit.SECONDS);Object result=second.get(5,TimeUnit.SECONDS);
                if (!transferFirst && side.equals("target")) {
                    assertThat(result).isInstanceOf(project_manager.web.ProjectMutationException.class);
                    assertThat(((project_manager.web.ProjectMutationException)result).getCode()).isEqualTo("PROJECT_NOT_FOUND");
                    assertThat(jdbc.queryForObject("select project_id from board_cards where id=?",String.class,card)).isEqualTo("p");
                    assertThat(jdbc.queryForObject("select title from board_cards where id=?",String.class,card)).isEqualTo("Original");
                } else {
                    assertThat(jdbc.queryForObject("select project_id from board_cards where id=?",String.class,card)).isEqualTo("r");
                    assertThat(jdbc.queryForObject("select title from board_cards where id=?",String.class,card)).isEqualTo("Moved");
                }
                if (transferFirst && side.equals("target")) assertThat(((JsonNode)result).path("impact")).isEqualTo(call(get("/api/projects/r/deletion-impact"),null,200));
                noOrphans();
            } finally { release.countDown(); }
        }
    }
    @Test void bothTransferDirectionsTakeProjectLocksInStableIdOrder() {
        String card=boards.createCard("p",cardRequest("Original")).id();clearInvocations(locks);
        boards.transferCard("p","r",card,cardRequest("Forward"));
        boards.transferCard("r","p",card,cardRequest("Back"));
        var order=inOrder(locks);
        order.verify(locks).requireBoth("p","r");order.verify(locks).require("p");order.verify(locks).require("r");
        order.verify(locks).requireBoth("r","p");order.verify(locks).require("p");order.verify(locks).require("r");
        noOrphans();
    }
    @Test void failureAfterActualDeleteAndFlushRestoresProjectAndEveryOtherRow() throws Exception {
        var before=snapshot();
        doAnswer(invocation -> {
            var counts=invocation.callRealMethod();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    assertThat(readOnly).isFalse();
                    assertThat(jdbc.queryForObject("select count(*) from projects where id='p'",Long.class)).isZero();
                    throw new org.springframework.dao.DataAccessResourceFailureException("SQL secret internal.Class C:\\private");
                }
            });return counts;
        }).when(readRepository).projectDeletionCounts("p");
        JsonNode error=call(delete(ROOT),null,500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR");
        assertThat(error.toString()).doesNotContain("SQL","secret","internal.Class","C:");
        assertThat(snapshot()).isEqualTo(before);noOrphans();
    }
    private void seed(String type,String project) {
        if (List.of("passport","component","integration","check","risk").contains(type) && jdbc.queryForObject("select count(*) from project_architecture_passports where project_id=?",Long.class,project)==0)
            jdbc.update("insert into project_architecture_passports(project_id,instance_id,criticality,stage,solution_type,version,created_at,updated_at) values (?,random_uuid(),'HIGH','NOT_STARTED','NEW_SOLUTION',5,'2026-01-01T00:00:00Z','2026-01-02T00:00:00Z')",project);
        String id=project+"-"+type;
        switch (type) {
            case "task" -> jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) values (?,?,'backlog',0,'Task','','','')",id,project);
            case "milestone" -> jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,position) values (?,?,'Point','2026-10-20',false,0)",id,project);
            case "component" -> jdbc.update("insert into project_architecture_components(id,project_id,name,component_type,criticality,external_component,position,version) values (?,?,'Component','BACKEND','LOW',false,0,3)",id,project);
            case "integration" -> {
                jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values (?,?,'Integration','Source','Target','OUTBOUND','API','NONE','LOW','READY',2)",id,project);
                jdbc.update("insert into project_integration_data_classes(integration_id,class_code) values (?,'PUBLIC')",id);
            }
            case "check" -> jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,version) values (?,?,'THREAT_MODEL',true,'NOT_STARTED',1)",id,project);
            case "risk" -> jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,version) values (?,?,'Risk','DATA','LOW','LOW','OPEN',4)",id,project);
            default -> { }
        }
    }
    private static String createPath(String type) { return ROOT+(type.equals("task")?"/board/cards":type.equals("milestone")?"/milestones":"/architecture"); }
    private static String createBody(String type) { return switch (type) {
        case "task" -> "{\"title\":\"Task\",\"description\":\"Description\",\"owner\":\"Owner\",\"dueDate\":\"2026-10-20\",\"columnKey\":\"backlog\",\"priority\":\"medium\",\"estimate\":1,\"blocked\":false}";
        case "milestone" -> "{\"expectedVersion\":1,\"name\":\"Point\",\"plannedDate\":\"2026-10-20\",\"completed\":false,\"position\":0}";
        default -> "{\"expectedProjectVersion\":1,\"criticality\":\"HIGH\",\"stage\":\"NOT_STARTED\",\"solutionType\":\"NEW_SOLUTION\"}";
    }; }
    private static project_manager.web.dto.BoardCardRequest cardRequest(String title) { return new project_manager.web.dto.BoardCardRequest(title,"Description","Owner","2026-10-20","backlog","medium","",1,false); }
    private JsonNode call(MockHttpServletRequestBuilder request,String body,int expected) throws Exception {
        if (body!=null) request.contentType(MediaType.APPLICATION_JSON).content(body);

        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }
    private JsonNode unchecked(MockHttpServletRequestBuilder request,String body,int expected) {
        try { return call(request,body,expected); } catch (Exception error) { throw new AssertionError(error); }
    }
    private JsonNode getCard() throws Exception { return call(get(ROOT),null,200); }
    private Map<String,List<Map<String,Object>>> snapshot() {
        var result = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table : TABLES) result.put(table,jdbc.queryForList("select * from "+table).stream()
            .sorted(java.util.Comparator.comparing(row -> String.valueOf(row.getOrDefault("ID",row.get("PROJECT_ID")))
                +":"+String.valueOf(row.getOrDefault("CLASS_CODE",row.get("ENVIRONMENT_CODE")))
                +":"+String.valueOf(row.get("INTEGRATION_ID")))).toList());
        return result;
    }
    private void noOrphans() {
        for (String table : List.of("board_cards","project_milestones"))
            assertThat(jdbc.queryForObject("select count(*) from "+table+" c left join projects p on p.id=c.project_id where p.id is null",Long.class)).isZero();
        for (String table : List.of("project_architecture_components","project_architecture_integrations","project_architecture_checks","project_security_risks","project_architecture_data_classes","project_architecture_environments"))
            assertThat(jdbc.queryForObject("select count(*) from "+table+" c left join project_architecture_passports p on p.project_id=c.project_id where p.project_id is null",Long.class)).as(table).isZero();
        assertThat(jdbc.queryForObject("select count(*) from project_integration_data_classes c left join project_architecture_integrations i on i.id=c.integration_id where i.id is null",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from project_architecture_passports a left join projects p on p.id=a.project_id where p.id is null",Long.class)).isZero();
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private void blocked(String table,Future<?> second) {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime()<deadline) {
            var rows = jdbc.queryForList("select session_state,blocker_id,executing_statement from information_schema.sessions");
            if (rows.stream().anyMatch(row -> "BLOCKED".equals(row.get("SESSION_STATE")) && row.get("BLOCKER_ID")!=null
                && String.valueOf(row.get("EXECUTING_STATEMENT")).toLowerCase().contains(table))) {
                assertThat(second.isDone()).isFalse(); return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
        }
        throw new AssertionError("Second transaction did not wait on expected lock");
    }
}
