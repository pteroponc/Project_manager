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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_arch_passport_deletion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ArchitecturePassportDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ArchitecturePassportDeletionService mutations;
    @MockitoSpyBean ArchitectureReadRepository readRepository;
    @TestBean(name = "architectureAssessmentClock", methodName = "fixedClock", enforceOverride = true) Clock clock;
    private static final String ROOT = "/api/projects/p/architecture";
    private static final List<String> TABLES = List.of("project_integration_data_classes", "project_architecture_components",
        "project_architecture_integrations", "project_architecture_checks", "project_security_risks", "project_architecture_data_classes",
        "project_architecture_environments", "project_architecture_passports", "project_milestones", "board_cards", "release_trains", "projects");
    static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"),ZoneOffset.UTC); }

    @BeforeEach
    void resetState() {
        reset(readRepository);
        TABLES.forEach(table -> jdbc.update("delete from " + table));
        for (String id : List.of("p","q","no-passport")) {
            jdbc.update("insert into projects(id,name,owner,status,health,budget,progress,quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,version) "
                + "values (?,?,'Owner','active','green',2147483647,0,'Q1','','','','','','','Summary',1)",id,id);
            if (!id.equals("no-passport")) {
                jdbc.update("insert into project_architecture_passports(instance_id,project_id,criticality,stage,solution_type,architect,solution_summary,availability_requirement,recovery_requirement,last_review_date,version,created_at,updated_at) "
                    + "values (random_uuid(),?,'HIGH','APPROVED','NEW_SOLUTION','Architect','Solution','Availability','Recovery','2026-10-01',0,'2026-01-01T00:00:00Z','2026-02-01T00:00:00Z')",id);
                jdbc.update("insert into project_architecture_data_classes(project_id,class_code) values (?,'PUBLIC')",id);
                jdbc.update("insert into project_architecture_environments(project_id,environment_code) values (?,'PROD')",id);
            }
        }
        prepareEmptyPassportAndOtherEntities();
    }

    private void prepareEmptyPassportAndOtherEntities() {
        jdbc.update("update project_architecture_passports set version=7");
        jdbc.update("delete from project_architecture_data_classes where project_id='p'");
        jdbc.update("delete from project_architecture_environments where project_id='p'");
        jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) values ('card','p','backlog',0,'Task','Description','Owner','')");
        jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,position) values ('milestone','p','Gate','2026-10-01',false,0)");
        jdbc.update("insert into release_trains(id,name,status,cadence,quarter,planned_release_date,code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) values ('train','Train','planned','monthly','Q1','','','','',0,0,0,0,'Scope','Risk','Decision')");
        jdbc.update("insert into project_architecture_components(id,project_id,name,component_type,criticality,external_component,position,version) values ('q-component','q','Component','BACKEND','HIGH',false,0,5)");
        jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values ('q-integration','q','Integration','Source','Target','OUTBOUND','API','NONE','LOW','READY',6)");
        jdbc.update("insert into project_integration_data_classes(integration_id,class_code) values ('q-integration','PUBLIC')");
        jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,version) values ('q-risk','q','Risk','DATA','LOW','LOW','MITIGATED',8)");
        jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,version) values ('q-check','q','THREAT_MODEL',true,'NOT_STARTED',9)");
    }

    @Test
    void deletesEmptyPassportOnlyAndReturnsMissingPassportAggregateWithProjectVersionIncrement() throws Exception {
        var before = snapshot(); JsonNode previous = getCard();
        JsonNode result = call(removal(7),null,200);
        assertMissing(result);
        assertThat(result.at("/project/version").asLong()).isEqualTo(2);
        ObjectNode project = previous.path("project").deepCopy(); project.put("version",2);
        assertThat(result.path("project")).isEqualTo(project);
        assertThat(getCard()).isEqualTo(result);
        before.put("project_architecture_passports",before.get("project_architecture_passports").stream().filter(row -> !row.get("PROJECT_ID").equals("p")).toList());
        before.get("projects").stream().filter(row -> row.get("ID").equals("p")).forEach(row -> row.put("VERSION",2L));
        assertThat(snapshot()).isEqualTo(before); noOrphans();
        assertThat(call(removal(7),null,404).path("code").asText()).isEqualTo("PASSPORT_NOT_FOUND");
        assertThat(snapshot()).isEqualTo(before);
        repeatGets(); assertThat(snapshot()).isEqualTo(before);
        JsonNode impact = call(get(ROOT+"/deletion-impact"),null,200);
        assertThat(impact.path("passportPresent").asBoolean()).isFalse(); assertThat(impact.path("deletionAllowed").asBoolean()).isFalse();
        assertThat(impact.path("blockers").get(0).asText()).isEqualTo("Паспорт отсутствует");
    }

    static Stream<String> dependencies() { return Stream.of("components","integrations","checks","risks","dataClasses","environments","all"); }
    @ParameterizedTest @MethodSource("dependencies")
    void eachDependencyAndTheirCombinationRefusesWithSameFreshImpactAsGetWithoutWrites(String kind) throws Exception {
        seedDependency(kind); var before = snapshot();
        JsonNode expected = call(get(ROOT+"/deletion-impact"),null,200);
        assertThat(expected.path("deletionAllowed").asBoolean()).isFalse();
        JsonNode error = call(removal(7),null,409);
        assertDependencies(error,expected);
        for (String field : List.of("componentCount","integrationCount","checkCount","securityRiskCount","dataClassCount","environmentCount")) {
            String key = switch (field) { case "componentCount" -> "components"; case "integrationCount" -> "integrations"; case "checkCount" -> "checks";
                case "securityRiskCount" -> "risks"; case "dataClassCount" -> "dataClasses"; default -> "environments"; };
            assertThat(error.path("impact").path(field).asLong()).as(field).isEqualTo(kind.equals("all")||kind.equals(key)?1:0);
        }
        assertThat(error.at("/impact/blockers").size()).isEqualTo(kind.equals("all")?6:1);
        assertThat(snapshot()).isEqualTo(before); repeatGets(); assertThat(snapshot()).isEqualTo(before); noOrphans();
    }

    @Test
    void dataClassesAndEnvironmentsMustBeExplicitlyClearedByPatchBeforeDeletion() throws Exception {
        seedDependency("dataClasses"); seedDependency("environments"); var before = snapshot();
        call(removal(7),null,409); assertThat(snapshot()).isEqualTo(before);
        JsonNode patched = call(patch(ROOT),"{\"expectedVersion\":7,\"dataClasses\":[],\"environments\":[]}",200);
        assertThat(patched.at("/passport/version").asLong()).isEqualTo(8);
        assertThat(patched.path("dataClasses").size()).isZero(); assertThat(patched.path("environments").size()).isZero();
        JsonNode deleted = call(removal(8),null,200); assertMissing(deleted);
        assertThat(deleted.at("/project/version").asLong()).isEqualTo(2);
    }

    @Test
    void missingResourcesAndStaleVersionAreSafeAndDoNotWrite() throws Exception {
        var before = snapshot();
        for (String project : List.of("missing","no-passport")) {
            JsonNode error = call(delete("/api/projects/"+project+"/architecture").queryParam("expectedVersion","7").queryParam("confirmed","true"),null,404);
            assertThat(error.path("code").asText()).isEqualTo(project.equals("missing")?"PROJECT_NOT_FOUND":"PASSPORT_NOT_FOUND");
            assertThat(error.has("currentVersion")).isFalse();
        }
        assertVersion(call(removal(0),null,409),"PASSPORT",7);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void strictSingletonQueryParametersAndExplicitConfirmationNeverWriteOnError() throws Exception {
        var before = snapshot();
        call(delete(ROOT),null,400); call(delete(ROOT).queryParam("expectedVersion","7"),null,400);
        call(delete(ROOT).queryParam("confirmed","true"),null,400);
        for (String value : List.of("","false","True","TRUE","1"," true ","null"))
            call(delete(ROOT).queryParam("expectedVersion","7").queryParam("confirmed",value),null,400);
        for (String value : List.of("","-1","+7","07"," 7 ","7.0","7e0","true","null","9223372036854775807","9223372036854775808","9".repeat(100)))
            call(delete(ROOT).queryParam("expectedVersion",value).queryParam("confirmed","true"),null,400);
        call(removal(7).queryParam("expectedVersion","7"),null,400);
        call(removal(7).queryParam("confirmed","true"),null,400);
        call(removal(7).queryParam("expectedPassportVersion","7"),null,400);
        call(removal(7).queryParam("extra","unknown"),null,400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void twoDeletesWaitOnProjectThenOneSucceedsAndOneReturnsMissingPassport() throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = unchecked(removal(7),null,200); written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(removal(7),null,404));
                blocked("projects",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertThat(second.get(5,TimeUnit.SECONDS).path("code").asText()).isEqualTo("PASSPORT_NOT_FOUND");
                assertMissing(getCard()); assertThat(getCard().at("/project/version").asLong()).isEqualTo(2); noOrphans();
            } finally { release.countDown(); }
        }
    }

    static Stream<Boolean> orders() { return Stream.of(true,false); }
    @ParameterizedTest @MethodSource("orders")
    void patchAndDeleteWaitOnPassportInBothOrdersAndPreserveWinner(boolean patchFirst) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        String body = "{\"expectedVersion\":7,\"architect\":\"Winner\"}";
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = patchFirst?unchecked(patch(ROOT),body,200):unchecked(removal(7),null,200);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> patchFirst?unchecked(removal(7),null,409):unchecked(patch(ROOT),body,404));
                blocked("project_architecture_passports",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                JsonNode result = second.get(5,TimeUnit.SECONDS);
                if (patchFirst) {
                    assertVersion(result,"PASSPORT",8);
                    assertThat(getCard().at("/passport/architect").asText()).isEqualTo("Winner");
                    assertThat(getCard().at("/passport/version").asLong()).isEqualTo(8);
                    assertThat(getCard().at("/project/version").asLong()).isEqualTo(1);
                } else {
                    assertThat(result.path("code").asText()).isEqualTo("PASSPORT_NOT_FOUND");
                    assertMissing(getCard()); assertThat(getCard().at("/project/version").asLong()).isEqualTo(2);
                }
                noOrphans();
            } finally { release.countDown(); }
        }
    }

    static Stream<Arguments> creationOrders() {
        return Stream.of("components","integrations","risks","initialize").flatMap(action -> Stream.of(Arguments.of(action,true),Arguments.of(action,false)));
    }
    @ParameterizedTest @MethodSource("creationOrders")
    void deleteAndCreationShareProjectLockInBothOrdersWithoutOrphans(String action,boolean deleteFirst) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = deleteFirst?unchecked(removal(7),null,200):unchecked(post(actionPath(action)),actionBody(action),201);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> deleteFirst?unchecked(post(actionPath(action)),actionBody(action),404):unchecked(removal(7),null,409));
                blocked("projects",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                JsonNode result = second.get(5,TimeUnit.SECONDS);
                if (deleteFirst) {
                    assertThat(result.path("code").asText()).isEqualTo("PASSPORT_NOT_FOUND");
                    assertMissing(getCard()); assertThat(getCard().at("/project/version").asLong()).isEqualTo(2);
                } else {
                    assertVersion(result,"PASSPORT",8);
                    JsonNode card = getCard();
                    assertThat(card.path(action.equals("initialize")?"checks":action).size()).isEqualTo(action.equals("initialize")?10:1);
                    assertThat(card.at("/project/version").asLong()).isEqualTo(1);
                    var before = snapshot();
                    JsonNode impact = call(get(ROOT+"/deletion-impact"),null,200);
                    assertDependencies(call(removal(8),null,409),impact,8);
                    assertThat(snapshot()).isEqualTo(before);
                }
                noOrphans();
            } finally { release.countDown(); }
        }
    }

    @Test
    void failureAfterDeleteFlushAndProjectIncrementRollsBackEveryFieldAndVersion() throws Exception {
        var before = snapshot();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from project_architecture_passports where project_id='p'",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select version from projects where id='p'",Long.class)).isEqualTo(2);
            throw new jakarta.persistence.PersistenceException("SQL secret internal.Class at C:\\private");
        }).when(readRepository).load(anyList());
        JsonNode error = call(removal(7),null,500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","internal.Class","C:");
        assertThat(snapshot()).isEqualTo(before); noOrphans();
        reset(readRepository); assertMissing(call(removal(7),null,200));
    }

    @Test
    void returnedDtoSeesOwnUncommittedDeletionAndGetNeverRecreatesPassport() throws Exception {
        var before = snapshot(); repeatGets(); assertThat(snapshot()).isEqualTo(before);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Card own = new TransactionTemplate(manager).execute(status -> {
                Card card = mutations.delete("p",Map.of("expectedPassportInstanceId",new String[]{ArchitectureTestTokens.token(jdbc,"p")},"expectedVersion",new String[]{"7"},"confirmed",new String[]{"true"}));
                assertThat(card.passport()).isNull(); assertThat(card.project().version()).isEqualTo(2);
                try {
                    JsonNode external = executor.submit(() -> getCard()).get(5,TimeUnit.SECONDS);
                    assertThat(external.path("passport").isNull()).isFalse(); assertThat(external.at("/project/version").asLong()).isEqualTo(1);
                } catch (Exception error) { throw new AssertionError(error); }
                return card;
            });
            assertThat(getCard()).isEqualTo(json.readTree(json.writeValueAsBytes(own)));
        }
        var after = snapshot(); repeatGets(); assertThat(snapshot()).isEqualTo(after); noOrphans();
    }

    private void seedDependency(String kind) {
        if (kind.equals("all")) { for (String key : List.of("components","integrations","checks","risks","dataClasses","environments")) seedDependency(key); return; }
        switch (kind) {
            case "components" -> jdbc.update("insert into project_architecture_components(id,project_id,name,component_type,criticality,external_component,position,version) values ('component','p','Component','BACKEND','HIGH',false,0,3)");
            case "integrations" -> {
                jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values ('integration','p','Integration','Source','Target','OUTBOUND','API','NONE','LOW','READY',3)");
                jdbc.update("insert into project_integration_data_classes(integration_id,class_code) values ('integration','PUBLIC')");
            }
            case "checks" -> jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,version) values ('check','p','THREAT_MODEL',true,'NOT_STARTED',3)");
            case "risks" -> jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,version) values ('risk','p','Risk','DATA','LOW','LOW','MITIGATED',3)");
            case "dataClasses" -> jdbc.update("insert into project_architecture_data_classes(project_id,class_code) values ('p','PUBLIC')");
            case "environments" -> jdbc.update("insert into project_architecture_environments(project_id,environment_code) values ('p','PROD')");
            default -> throw new AssertionError("Unexpected dependency");
        }
    }

    static Stream<String> oldPassportRequests() { return Stream.of("patch","delete","components","integrations","risks","initialize"); }
    @ParameterizedTest @MethodSource("oldPassportRequests")
    void staleRequestFromDeletedPassportMustNotAffectRecreatedPassport(String action) throws Exception {
        String root = "/api/projects/no-passport/architecture";
        String createA = "{\"expectedProjectVersion\":1,\"criticality\":\"HIGH\",\"stage\":\"NOT_STARTED\",\"solutionType\":\"NEW_SOLUTION\"}";
        JsonNode a = call(post(root),createA,201);
        assertThat(a.at("/passport/version").asLong()).isZero();
        // Capture the complete old command before A is removed. Use the same frozen Clock for A and B.
        MockHttpServletRequestBuilder saved;
        String instanceA=a.at("/passport/instanceId").asText();
        if (action.equals("patch")) saved = patch(root).contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("expectedVersion",0).put("expectedPassportInstanceId",instanceA).put("architect","Stale A").toString());
        else if (action.equals("delete")) saved = delete(root).queryParam("expectedVersion","0").queryParam("confirmed","true").queryParam("expectedPassportInstanceId",instanceA);
        else {
            ObjectNode body = (ObjectNode) json.readTree(actionBody(action)); body.put("expectedPassportVersion",0).put("expectedPassportInstanceId",instanceA);
            saved = post(action.equals("initialize")?root+"/checks/initialize":root+"/"+action)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString());
        }
        call(delete(root).queryParam("expectedVersion","0").queryParam("confirmed","true"),null,200);
        JsonNode b = call(post(root),createA.replace("\"expectedProjectVersion\":1","\"expectedProjectVersion\":3"),201);
        assertThat(b.at("/passport/version").asLong()).isZero();
        assertThat(b.at("/passport/instanceId").asText()).isNotEqualTo(instanceA);
        var before = snapshot();
        var response = mvc.perform(saved).andReturn().getResponse();
        // This is a regression requirement, not an assertion that unsafe acceptance is expected.
        assertThat(response.getStatus()).as("Old %s command was accepted",action).isEqualTo(409);
        assertThat(json.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("PASSPORT_INSTANCE_CONFLICT");
        assertThat(snapshot()).as("Old command must preserve every row of B").isEqualTo(before);
        assertThat(call(get(root),null,200)).isEqualTo(b);
        MockHttpServletRequestBuilder current=instanceRequest(action,root,b.at("/passport/instanceId").asText(),0);
        var currentResponse=mvc.perform(current).andReturn().getResponse();
        assertThat(currentResponse.getStatus()).isEqualTo(action.equals("patch")||action.equals("delete")?200:201);
        JsonNode updated=json.readTree(currentResponse.getContentAsString());
        if (!action.equals("delete")) assertThat(updated.at("/passport/instanceId")).isEqualTo(b.at("/passport/instanceId"));
    }

    static Stream<String> protectedOperations() { return Stream.of("patch","delete","components","integrations","risks","initialize","delete-components","delete-integrations","delete-checks","delete-risks"); }
    @ParameterizedTest @MethodSource("protectedOperations")
    void instanceTokenIsMandatoryStrictAndCheckedBeforeNumbersWithoutAnyMutation(String action) throws Exception {
        seedDependency("all"); var before=snapshot();
        String valid=ArchitectureTestTokens.token(jdbc,"p");
        for (String value : new String[]{null,"","1-1-1-1-1","not-a-uuid"," "+valid,valid+" ","x".repeat(37)}) {
            var request=instanceRequest(action,ROOT,value,7);
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).as(action+" invalid token").isEqualTo(400);
            assertThat(snapshot()).isEqualTo(before);
        }
        var stale=instanceRequest(action,ROOT,java.util.UUID.randomUUID().toString(),999);
        var response=mvc.perform(stale).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(409);
        JsonNode error=json.readTree(response.getContentAsString());
        assertThat(error.path("code").asText()).isEqualTo("PASSPORT_INSTANCE_CONFLICT");
        assertThat(error.path("currentVersion").asLong()).isEqualTo(7);
        assertThat(error.path("versionResource").asText()).isEqualTo("PASSPORT");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void waitingOldPatchIsRejectedAfterDeleteAndRecreateCommits() throws Exception {
        String instanceA=ArchitectureTestTokens.token(jdbc,"p");
        CountDownLatch recreated=new CountDownLatch(1),release=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                unchecked(removal(7).queryParam("expectedPassportInstanceId",instanceA),null,200);
                // Finish deletion+recreation in the same held transaction; no wall-clock identity is used.
                JsonNode b=unchecked(post(ROOT),"{\"expectedProjectVersion\":2,\"criticality\":\"HIGH\",\"stage\":\"NOT_STARTED\",\"solutionType\":\"NEW_SOLUTION\"}",201);
                recreated.countDown(); await(release); return b;
            }));
            try {
                assertThat(recreated.await(5,TimeUnit.SECONDS)).isTrue();
                var second=executor.submit(() -> {
                    try { return mvc.perform(instanceRequest("patch",ROOT,instanceA,0)).andReturn().getResponse(); }
                    catch (Exception error) { throw new AssertionError(error); }
                });
                blocked("project_architecture_passports",second); release.countDown(); JsonNode b=first.get(5,TimeUnit.SECONDS);
                var response=second.get(5,TimeUnit.SECONDS);
                // H2 may report the deleted selected A as absent after waiting, rather than select replacement B.
                assertThat(response.getStatus()).isIn(404,409);
                assertThat(json.readTree(response.getContentAsString()).path("code").asText())
                    .isEqualTo(response.getStatus()==404?"PASSPORT_NOT_FOUND":"PASSPORT_INSTANCE_CONFLICT");
                assertThat(getCard()).isEqualTo(b); noOrphans();
                var replay=mvc.perform(instanceRequest("patch",ROOT,instanceA,0)).andReturn().getResponse();
                assertThat(replay.getStatus()).isEqualTo(409);
                assertThat(json.readTree(replay.getContentAsString()).path("code").asText()).isEqualTo("PASSPORT_INSTANCE_CONFLICT");
                assertThat(getCard()).isEqualTo(b);
            } finally { release.countDown(); }
        }
    }

    private MockHttpServletRequestBuilder instanceRequest(String action,String root,String instance,long version) throws Exception {
        if (action.equals("delete")||action.startsWith("delete-")) {
            String collection=action.startsWith("delete-")?action.substring(7):null;
            String record=collection==null?null:switch(collection) { case "components"->"component"; case "integrations"->"integration"; case "checks"->"check"; default->"risk"; };
            var request=delete(collection==null?root:root+"/"+collection+"/"+record).queryParam("expectedVersion",Long.toString(version)).queryParam("confirmed","true");
            if (collection!=null) request.queryParam("expectedPassportVersion",Long.toString(version));
            if (instance!=null) request.queryParam("expectedPassportInstanceId",instance);
            return request;
        }
        ObjectNode body=action.equals("patch")?json.createObjectNode().put("expectedVersion",version).put("architect","Current B"):(ObjectNode)json.readTree(actionBody(action));
        if (!action.equals("patch")) body.put("expectedPassportVersion",version);
        if (instance!=null) body.put("expectedPassportInstanceId",instance);
        return (action.equals("patch")?patch(root):post(action.equals("initialize")?root+"/checks/initialize":root+"/"+action)).contentType(MediaType.APPLICATION_JSON).content(body.toString());
    }

    static Stream<String> childKinds() { return Stream.of("components","integrations","checks","risks"); }
    @ParameterizedTest @MethodSource("childKinds")
    void deletedChildIdsAreNotReusedAndOldDeleteAndPatchCannotAffectB(String collection) throws Exception {
        String root="/api/projects/no-passport/architecture";
        String create="{\"expectedProjectVersion\":1,\"criticality\":\"HIGH\",\"stage\":\"NOT_STARTED\",\"solutionType\":\"NEW_SOLUTION\"}";
        JsonNode a=call(post(root),create,201); String instanceA=a.at("/passport/instanceId").asText();
        String action=collection.equals("checks")?"initialize":collection;
        var response=mvc.perform(instanceRequest(action,root,instanceA,0)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode children=json.readTree(response.getContentAsString()).path(collection);
        String oldId=children.get(0).path("id").asText();
        var saved=delete(root+"/"+collection+"/"+oldId).queryParam("expectedVersion","0").queryParam("expectedPassportVersion","1")
            .queryParam("expectedPassportInstanceId",instanceA).queryParam("confirmed","true");
        int passportVersion=1;
        for (JsonNode child : children)
            call(delete(root+"/"+collection+"/"+child.path("id").asText()).queryParam("expectedVersion","0")
                .queryParam("expectedPassportVersion",Integer.toString(passportVersion++)).queryParam("expectedPassportInstanceId",instanceA).queryParam("confirmed","true"),null,200);
        call(delete(root).queryParam("expectedVersion",Integer.toString(passportVersion)).queryParam("expectedPassportInstanceId",instanceA).queryParam("confirmed","true"),null,200);
        JsonNode b=call(post(root),create.replace("\"expectedProjectVersion\":1","\"expectedProjectVersion\":3"),201);
        String instanceB=b.at("/passport/instanceId").asText();
        var current=mvc.perform(instanceRequest(action,root,instanceB,0)).andReturn().getResponse();
        assertThat(current.getStatus()).isEqualTo(201);
        assertThat(json.readTree(current.getContentAsString()).path(collection).findValuesAsText("id")).doesNotContainAnyElementsOf(children.findValuesAsText("id"));
        var before=snapshot();
        assertThat(mvc.perform(saved).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(patch(root+"/"+collection+"/"+oldId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":0,\""+(collection.equals("checks")?"owner":"name")+"\":\"Stale\"}")).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(snapshot()).isEqualTo(before); noOrphans();
    }

    @Test
    void rejectsClientAssignedInstanceAndWrongJsonTokenTypesOrDuplicates() throws Exception {
        String token=ArchitectureTestTokens.token(jdbc,"p"); var before=snapshot();
        for (String action : new String[]{"patch","components","integrations","risks","initialize"}) {
            for (String invalid : new String[]{"null","true","1","[]","{}"}) {
                var request=instanceRequest(action,ROOT,token,7);
                String raw=new String(request.buildRequest(new org.springframework.mock.web.MockServletContext()).getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                raw=raw.replace("\"expectedPassportInstanceId\":\""+token+"\"","\"expectedPassportInstanceId\":"+invalid);
                request.content(raw);
                assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(400);
            }
            var duplicate=instanceRequest(action,ROOT,token,7);
            String raw=new String(duplicate.buildRequest(new org.springframework.mock.web.MockServletContext()).getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            duplicate.content(raw.substring(0,raw.length()-1)+",\"expectedPassportInstanceId\":\""+token+"\"}");
            assertThat(mvc.perform(duplicate).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(mvc.perform(delete(ROOT).queryParam("expectedVersion","7").queryParam("confirmed","true")
            .queryParam("expectedPassportInstanceId",token,token)).andReturn().getResponse().getStatus()).isEqualTo(400);
        call(post("/api/projects/no-passport/architecture"),"{\"expectedProjectVersion\":1,\"criticality\":\"HIGH\",\"stage\":\"NOT_STARTED\",\"solutionType\":\"NEW_SOLUTION\",\"instanceId\":\""+token+"\"}",400);
        assertThat(mvc.perform(instanceRequest("patch",ROOT,token,7).content("{\"expectedVersion\":7,\"expectedPassportInstanceId\":\""+token+"\",\"instanceId\":\""+token+"\"}")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
    }
    private void repeatGets() throws Exception {
        for (int i=0;i<3;i++) for (String path : List.of(ROOT,ROOT+"/deletion-impact","/api/projects/p/deletion-impact","/api/architecture/portfolio","/api/projects/q/architecture","/api/projects/no-passport/architecture")) call(get(path),null,200);
    }
    private static void assertMissing(JsonNode card) {
        assertThat(card.path("passport").isNull()).isTrue();
        for (String collection : List.of("dataClasses","environments","components","integrations","checks","risks")) assertThat(card.path(collection).size()).as(collection).isZero();
        assertThat(card.at("/assessment/status").asText()).isEqualTo("NOT_ASSESSED");
        assertThat(codes(card)).contains("PASSPORT_MISSING");
        assertThat(card.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        assertThat(card.toString()).doesNotContain("budget");
    }
    private static void assertDependencies(JsonNode error,JsonNode impact) {
        assertDependencies(error,impact,7);
    }
    private static void assertDependencies(JsonNode error,JsonNode impact,long version) {
        assertThat(error.path("code").asText()).isEqualTo("PASSPORT_HAS_DEPENDENCIES");
        assertThat(error.path("currentVersion").asLong()).isEqualTo(version);
        assertThat(error.path("versionResource").asText()).isEqualTo("PASSPORT");
        assertThat(error.path("impact")).isEqualTo(impact);
    }
    private static MockHttpServletRequestBuilder removal(long version) { return delete(ROOT).queryParam("expectedVersion",Long.toString(version)).queryParam("confirmed","true"); }
    private static String actionPath(String action) { return action.equals("initialize")?ROOT+"/checks/initialize":ROOT+"/"+action; }
    private String actionBody(String action) {
        ObjectNode body = json.createObjectNode().put("expectedPassportVersion",7);
        if (action.equals("initialize")) return body.toString();
        body.put("name","Name");
        if (action.equals("risks")) body.put("category","DATA").put("probability","LOW").put("impact","LOW").put("status","OPEN");
        else {
            body.put("criticality","HIGH");
            if (action.equals("components")) body.put("componentType","BACKEND").put("externalComponent",false).put("position",0);
            else { body.put("sourceSystem","Source").put("targetSystem","Target").put("direction","OUTBOUND").put("interactionType","API").put("authCategory","OAUTH2").put("readiness","READY"); body.putArray("dataClasses").add("PUBLIC"); }
        }
        return body.toString();
    }
    private static List<String> codes(JsonNode card) { return card.at("/assessment/reasons").findValuesAsText("code"); }
    private static void assertVersion(JsonNode error,String resource,long expected) {
        assertThat(error.path("code").asText()).isEqualTo(resource+"_VERSION_CONFLICT");
        assertThat(error.path("versionResource").asText()).isEqualTo(resource); assertThat(error.path("currentVersion").asLong()).isEqualTo(expected);
    }
    private JsonNode call(MockHttpServletRequestBuilder request,String body,int expected) throws Exception {
        if (body!=null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        ArchitectureTestTokens.supply(request,jdbc,json);
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
