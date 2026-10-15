package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import project_manager.service.ArchitectureChildDeletionService;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_arch_deletions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ArchitectureChildDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ArchitectureChildDeletionService mutations;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoSpyBean project_manager.service.ArchitectureIntegrationClassWriter classes;
    @MockitoSpyBean ArchitectureReadRepository readRepository;
    @TestBean(name = "architectureAssessmentClock", methodName = "fixedClock", enforceOverride = true) Clock clock;
    private static final String ROOT = "/api/projects/p/architecture";
    private static final List<String> TABLES = List.of("project_integration_data_classes", "project_architecture_components",
        "project_architecture_integrations", "project_architecture_checks", "project_security_risks", "project_architecture_data_classes",
        "project_architecture_environments", "project_architecture_passports", "project_milestones", "board_cards", "release_trains", "projects");
    static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"),ZoneOffset.UTC); }

    @BeforeEach
    void resetState() {
        reset(readRepository,classes);
        TABLES.forEach(table -> jdbc.update("delete from " + table));
        for (String id : List.of("p","q","no-passport")) {
            jdbc.update("insert into projects(id,name,owner,status,health,budget,progress,quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,version) "
                + "values (?,?,'Owner','active','green',2147483647,0,'Q1','','','','','','','Summary',1)",id,id);
            if (!id.equals("no-passport")) {
                jdbc.update("insert into project_architecture_passports(project_id,criticality,stage,solution_type,architect,solution_summary,availability_requirement,recovery_requirement,last_review_date,version,created_at,updated_at) "
                    + "values (?,'HIGH','APPROVED','NEW_SOLUTION','Architect','Solution','Availability','Recovery','2026-10-01',0,'2026-01-01T00:00:00Z','2026-02-01T00:00:00Z')",id);
                jdbc.update("insert into project_architecture_data_classes(project_id,class_code) values (?,'PUBLIC')",id);
                jdbc.update("insert into project_architecture_environments(project_id,environment_code) values (?,'PROD')",id);
            }
        }
        seedChildren();
    }

    private static final List<String> COLLECTIONS = List.of("components","integrations","checks","risks");
    private static String resource(String collection) { return switch (collection) {
        case "components" -> "COMPONENT"; case "integrations" -> "INTEGRATION"; case "checks" -> "CHECK"; default -> "RISK";
    }; }
    static Stream<String> collections() { return COLLECTIONS.stream(); }
    private static String table(String collection) { return collection.equals("risks")?"project_security_risks":"project_architecture_"+collection; }

    private void seedChildren() {
        jdbc.update("update project_architecture_passports set version=7");
        for (String project : List.of("p","q")) {
            String prefix = project.equals("p")?"":"q-";
            for (int i=1;i<=2;i++) {
                jdbc.update("insert into project_architecture_components(id,project_id,name,component_type,criticality,external_component,position,version) values (?,?,?,'BACKEND','HIGH',false,?,3)",prefix+"components-"+i,project,"Component "+i,i);
                jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values (?,?,?,'Source','Target','OUTBOUND','API','NONE','LOW','READY',3)",prefix+"integrations-"+i,project,"Integration "+i);
                for (String code : i==1?List.of("PUBLIC","INTERNAL"):List.of("PERSONAL"))
                    jdbc.update("insert into project_integration_data_classes(integration_id,class_code) values (?,?)",prefix+"integrations-"+i,code);
                jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,version) values (?,?,?,'DATA','LOW','LOW','MITIGATED',3)",prefix+"risks-"+i,project,"Risk "+i);
            }
            int i=1;
            for (String type : ArchitectureReadinessAssessmentService.CHECK_TYPES)
                jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,actual_date,version) values (?,?,?,true,'PASSED','2026-10-14',3)",prefix+"checks-"+i++,project,type);
        }
        jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) values ('card','p','backlog',0,'Task','Description','Owner','')");
        jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,position) values ('milestone','p','Gate','2026-10-01',false,0)");
        jdbc.update("insert into release_trains(id,name,status,cadence,quarter,planned_release_date,code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) values ('train','Train','planned','monthly','Q1','','','','',0,0,0,0,'Scope','Risk','Decision')");
    }

    @ParameterizedTest @MethodSource("collections")
    void deletesOnlyTargetAndReturnsFullAggregateWithExactlyOnePassportIncrement(String collection) throws Exception {
        var before = snapshot(); JsonNode previous = getCard();
        JsonNode result = call(removal(collection,collection+"-1",3,7),null,200);
        assertThat(result.path(collection).findValuesAsText("id")).doesNotContain(collection+"-1").contains(collection+"-2");
        assertThat(result.at("/passport/version").asLong()).isEqualTo(8);
        assertThat(result.at("/passport/documentVersion").asText()).isEqualTo("1.0");
        assertThat(result.path("project")).isEqualTo(previous.path("project"));
        for (String field : List.of("createdAt","updatedAt")) assertThat(result.path("passport").path(field)).isEqualTo(previous.path("passport").path(field));
        assertThat(result.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        assertThat(result.toString()).doesNotContain("budget");
        assertThat(getCard()).isEqualTo(result);
        before.put(table(collection),before.get(table(collection)).stream().filter(row -> !row.get("ID").equals(collection+"-1")).toList());
        if (collection.equals("integrations")) before.put("project_integration_data_classes",before.get("project_integration_data_classes").stream()
            .filter(row -> !row.get("INTEGRATION_ID").equals("integrations-1")).toList());
        before.get("project_architecture_passports").stream().filter(row -> row.get("PROJECT_ID").equals("p")).forEach(row -> row.put("VERSION",8L));
        assertThat(snapshot()).isEqualTo(before); noOrphans();
        JsonNode gone = call(removal(collection,collection+"-1",3,7),null,404);
        assertThat(gone.path("code").asText()).isEqualTo(resource(collection)+"_NOT_FOUND");
        assertThat(gone.has("currentVersion")).isFalse(); assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @MethodSource("collections")
    void validatesParametersAndConfirmationBeforeAnyWrite(String collection) throws Exception {
        var before = snapshot(); String path = ROOT+"/"+collection+"/"+collection+"-1";
        for (String omitted : List.of("expectedVersion","expectedPassportVersion","confirmed")) {
            var request = delete(path);
            if (!omitted.equals("expectedVersion")) request.queryParam("expectedVersion","3");
            if (!omitted.equals("expectedPassportVersion")) request.queryParam("expectedPassportVersion","7");
            if (!omitted.equals("confirmed")) request.queryParam("confirmed","true");
            call(request,null,400);
        }
        for (String value : List.of("","false","TRUE","True","1"," true ","null"))
            call(delete(path).queryParam("expectedVersion","3").queryParam("expectedPassportVersion","7").queryParam("confirmed",value),null,400);
        for (String field : List.of("expectedVersion","expectedPassportVersion")) for (String value : List.of("","-1","+3"," 3 ","3.0","03","null","true","9223372036854775807","9223372036854775808","9".repeat(100))) {
            var request = delete(path).queryParam("confirmed","true");
            request.queryParam("expectedVersion",field.equals("expectedVersion")?value:"3");
            request.queryParam("expectedPassportVersion",field.equals("expectedPassportVersion")?value:"7");
            call(request,null,400);
        }
        for (String duplicate : List.of("expectedVersion","expectedPassportVersion","confirmed"))
            call(removal(collection,collection+"-1",3,7).queryParam(duplicate,duplicate.equals("confirmed")?"true":"3"),null,400);
        call(removal(collection,collection+"-1",3,7).queryParam("extra","ignored"),null,400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @MethodSource("collections")
    void missingParentsForeignRecordsAndBothVersionConflictsAreSafe(String collection) throws Exception {
        var before = snapshot();
        for (String project : List.of("missing","no-passport")) {
            JsonNode error = call(delete("/api/projects/"+project+"/architecture/"+collection+"/"+collection+"-1")
                .queryParam("expectedVersion","3").queryParam("expectedPassportVersion","7").queryParam("confirmed","true"),null,404);
            assertThat(error.path("code").asText()).isEqualTo(project.equals("missing")?"PROJECT_NOT_FOUND":"PASSPORT_NOT_FOUND");
        }
        for (String id : List.of("missing","q-"+collection+"-1")) {
            JsonNode error = call(removal(collection,id,999,999),null,404);
            assertThat(error.path("code").asText()).isEqualTo(resource(collection)+"_NOT_FOUND");
            assertThat(error.has("currentVersion")).isFalse();
        }
        assertVersion(call(removal(collection,collection+"-1",3,0),null,409),"PASSPORT",7);
        assertVersion(call(removal(collection,collection+"-1",0,7),null,409),resource(collection),3);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @MethodSource("collections")
    void twoDeletesOfSameRecordWaitThenReturn404(String collection) throws Exception {
        runHeldDelete(collection,collection+"-1", () -> {
            JsonNode error = unchecked(removal(collection,collection+"-1",3,7),null,404);
            assertThat(error.path("code").asText()).isEqualTo(resource(collection)+"_NOT_FOUND"); return error;
        });
        assertThat(getCard().path(collection).findValuesAsText("id")).doesNotContain(collection+"-1");
        assertThat(getCard().at("/passport/version").asLong()).isEqualTo(8); noOrphans();
    }

    @ParameterizedTest @MethodSource("collections")
    void twoDeletesOfDifferentRecordsSharePassportVersion(String collection) throws Exception {
        runHeldDelete(collection,collection+"-1", () -> {
            JsonNode error = unchecked(removal(collection,collection+"-2",3,7),null,409);
            assertVersion(error,"PASSPORT",8); return error;
        });
        JsonNode result = getCard(); assertThat(result.path(collection).findValuesAsText("id")).contains(collection+"-2").doesNotContain(collection+"-1");
        assertThat(result.at("/passport/version").asLong()).isEqualTo(8); noOrphans();
    }

    static Stream<Arguments> patchOrders() { return COLLECTIONS.stream().flatMap(c -> Stream.of(Arguments.of(c,true),Arguments.of(c,false))); }
    @ParameterizedTest @MethodSource("patchOrders")
    void patchAndDeleteActuallyWaitInBothOrdersWithoutLosingChanges(String collection,boolean patchFirst) throws Exception {
        String id = collection+"-1";
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = patchFirst?unchecked(patch(ROOT+"/"+collection+"/"+id),patchBody(collection),200):unchecked(removal(collection,id,3,7),null,200);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> patchFirst?unchecked(removal(collection,id,3,7),null,409):unchecked(patch(ROOT+"/"+collection+"/"+id),patchBody(collection),404));
                blocked(table(collection),second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                JsonNode result = second.get(5,TimeUnit.SECONDS);
                if (patchFirst) {
                    assertVersion(result,resource(collection),4);
                    JsonNode row = row(getCard(),collection,id);
                    assertThat(row.path(collection.equals("checks")?"owner":"name").asText()).isEqualTo("Winner");
                    assertThat(row.path("version").asLong()).isEqualTo(4);
                    assertThat(getCard().at("/passport/version").asLong()).isEqualTo(7);
                } else {
                    assertThat(result.path("code").asText()).isEqualTo(resource(collection)+"_NOT_FOUND");
                    assertThat(getCard().path(collection).findValuesAsText("id")).doesNotContain(id);
                    assertThat(getCard().at("/passport/version").asLong()).isEqualTo(8);
                }
                noOrphans();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest @MethodSource("patchOrders")
    void deleteAndCreateSharePassportVersionInBothOrders(String collection,boolean deleteFirst) throws Exception {
        String id = collection+"-1";
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = deleteFirst?unchecked(removal(collection,id,3,7),null,200):unchecked(post(ROOT+"/components"),componentBody(),201);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> deleteFirst?unchecked(post(ROOT+"/components"),componentBody(),409):unchecked(removal(collection,id,3,7),null,409));
                blocked("projects",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertVersion(second.get(5,TimeUnit.SECONDS),"PASSPORT",8);
                JsonNode result = getCard();
                assertThat(result.path(collection).findValuesAsText("id").contains(id)).isEqualTo(!deleteFirst);
                assertThat(result.path("components").size()).isEqualTo(deleteFirst?(collection.equals("components")?1:2):3);
                assertThat(result.at("/passport/version").asLong()).isEqualTo(8); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest @MethodSource("collections")
    void failureBuildingDtoAfterRealDeleteAndFlushRestoresAllRowsAndVersions(String collection) throws Exception {
        var before = snapshot();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from "+table(collection)+" where id=?",Long.class,collection+"-1")).isZero();
            if (collection.equals("integrations")) assertThat(jdbc.queryForObject("select count(*) from project_integration_data_classes where integration_id='integrations-1'",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select version from project_architecture_passports where project_id='p'",Long.class)).isEqualTo(8);
            throw new jakarta.persistence.PersistenceException("SQL secret internal.Class at C:\\private");
        }).when(readRepository).load(anyList());
        JsonNode error = call(removal(collection,collection+"-1",3,7),null,500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","internal.Class","C:");
        assertThat(snapshot()).isEqualTo(before); noOrphans();
    }

    @Test
    void integrationFailureAfterClassificationDeleteAndFlushRestoresClassesAndRow() throws Exception {
        var before = snapshot();
        doAnswer(invocation -> {
            invocation.callRealMethod(); em.flush();
            assertThat(jdbc.queryForObject("select count(*) from project_integration_data_classes where integration_id='integrations-1'",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from project_architecture_integrations where id='integrations-1'",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select version from project_architecture_passports where project_id='p'",Long.class)).isEqualTo(7);
            throw new org.springframework.dao.DataAccessResourceFailureException("SQL secret at C:\\private");
        }).when(classes).replace(any(),anySet());
        JsonNode error = call(removal("integrations","integrations-1",3,7),null,500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","C:");
        assertThat(snapshot()).isEqualTo(before); noOrphans();
    }

    @Test
    void deletingPassedCheckRemovesReadyAndGetsNeverRestoreItOrAnythingElse() throws Exception {
        assertThat(getCard().at("/assessment/status").asText()).isEqualTo("READY");
        JsonNode result = call(removal("checks","checks-1",3,7),null,200);
        assertThat(result.path("checks").size()).isEqualTo(9);
        assertThat(result.at("/assessment/status").asText()).isEqualTo("NOT_ASSESSED");
        assertThat(codes(result)).contains("CHECK_TYPE_MISSING","CHECKS_INCOMPLETE");
        var before = snapshot(); repeatGets(); assertThat(snapshot()).isEqualTo(before);
        assertThat(call(post(ROOT+"/checks/initialize"),"{\"expectedPassportVersion\":8}",409).path("code").asText()).isEqualTo("CHECKS_ALREADY_EXIST");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void deletionRecalculatesRiskAndIntegrationReasonsWithoutLosingOtherReasons() throws Exception {
        jdbc.update("update project_security_risks set probability='HIGH',impact='HIGH',status='OPEN' where id='risks-1'");
        jdbc.update("update project_security_risks set probability='LOW',impact='CRITICAL',status='ACCEPTED',acceptance_justification='Decision' where id='risks-2'");
        jdbc.update("update project_architecture_integrations set criticality='HIGH',readiness='BLOCKED' where id='integrations-1'");
        assertThat(codes(getCard())).contains("RISK_CRITICAL_OPEN","ACCEPTED_HIGH_RISK","INTEGRATION_BLOCKED");
        JsonNode riskRemoved = call(removal("risks","risks-1",3,7),null,200);
        assertThat(codes(riskRemoved)).contains("ACCEPTED_HIGH_RISK","INTEGRATION_BLOCKED").doesNotContain("RISK_CRITICAL_OPEN");
        JsonNode integrationRemoved = call(removal("integrations","integrations-1",3,8),null,200);
        assertThat(codes(integrationRemoved)).contains("ACCEPTED_HIGH_RISK").doesNotContain("INTEGRATION_BLOCKED");
        assertThat(integrationRemoved.at("/assessment/status").asText()).isEqualTo("ATTENTION_REQUIRED");
        assertThat(integrationRemoved.at("/assessment/acceptedHighRisks/0/acceptanceVerification").asText()).isEqualTo("UNVERIFIED");
        assertThat(integrationRemoved.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
    }

    @Test
    void responseSeesUncommittedDeletionWhileOtherGetStillSeesOriginal() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Card own = new TransactionTemplate(manager).execute(status -> {
                Card card = mutations.delete("p","integrations-1",ArchitectureChildDeletionService.Kind.INTEGRATION,
                    Map.of("expectedVersion",new String[]{"3"},"expectedPassportVersion",new String[]{"7"},"confirmed",new String[]{"true"}));
                assertThat(card.integrations()).hasSize(1); assertThat(card.passport().version()).isEqualTo(8);
                try {
                    JsonNode external = executor.submit(() -> getCard()).get(5,TimeUnit.SECONDS);
                    assertThat(external.path("integrations").size()).isEqualTo(2); assertThat(external.at("/passport/version").asLong()).isEqualTo(7);
                } catch (Exception error) { throw new AssertionError(error); }
                return card;
            });
            assertThat(getCard()).isEqualTo(json.readTree(json.writeValueAsBytes(own))); noOrphans();
        }
        var before = snapshot(); repeatGets(); assertThat(snapshot()).isEqualTo(before);
    }

    private void runHeldDelete(String collection,String id,java.util.concurrent.Callable<JsonNode> secondCall) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = unchecked(removal(collection,id,3,7),null,200);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(secondCall); blocked("projects",second);
                release.countDown(); first.get(5,TimeUnit.SECONDS); second.get(5,TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
    }
    private static MockHttpServletRequestBuilder removal(String collection,String id,long version,long passportVersion) {
        return delete(ROOT+"/"+collection+"/"+id).queryParam("expectedVersion",Long.toString(version))
            .queryParam("expectedPassportVersion",Long.toString(passportVersion)).queryParam("confirmed","true");
    }
    private static String patchBody(String collection) { return "{\"expectedVersion\":3,\""+(collection.equals("checks")?"owner":"name")+"\":\"Winner\"}"; }
    private static String componentBody() { return "{\"expectedPassportVersion\":7,\"name\":\"New component\",\"componentType\":\"BACKEND\",\"criticality\":\"LOW\",\"externalComponent\":false,\"position\":0}"; }
    private static JsonNode row(JsonNode card,String collection,String id) { for (JsonNode row : card.path(collection)) if (row.path("id").asText().equals(id)) return row; throw new AssertionError("Missing record"); }
    private void repeatGets() throws Exception {
        for (int repeat=0;repeat<3;repeat++) for (String path : List.of(ROOT,ROOT+"/deletion-impact","/api/projects/p/deletion-impact",
            "/api/architecture/portfolio","/api/projects/q/architecture","/api/projects/no-passport/architecture")) call(get(path),null,200);
    }
    private static List<String> codes(JsonNode card) { return card.at("/assessment/reasons").findValuesAsText("code"); }
    private static void assertVersion(JsonNode error,String resource,long expected) {
        assertThat(error.path("code").asText()).isEqualTo(resource+"_VERSION_CONFLICT");
        assertThat(error.path("versionResource").asText()).isEqualTo(resource); assertThat(error.path("currentVersion").asLong()).isEqualTo(expected);
    }
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
        for (String collection : COLLECTIONS)
            assertThat(jdbc.queryForObject("select count(*) from "+table(collection)+" c left join project_architecture_passports p on p.project_id=c.project_id where p.project_id is null",Long.class)).as(collection).isZero();
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
