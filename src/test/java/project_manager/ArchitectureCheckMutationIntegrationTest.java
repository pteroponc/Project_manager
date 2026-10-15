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
import project_manager.service.ArchitectureCheckMutationService;
import project_manager.service.ArchitectureReadinessAssessmentService;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_arch_checks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ArchitectureCheckMutationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ArchitectureCheckMutationService mutations;
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
    }

    @Test
    void initializeCreatesExactlyTenDefaultRecordsAndOnePassportIncrement() throws Exception {
        JsonNode before = getCard();
        assertThat(before.path("checks").size()).isZero();
        JsonNode result = initialize();
        assertThat(result.path("checks").size()).isEqualTo(10);
        assertThat(result.path("checks").findValuesAsText("checkType")).containsExactlyElementsOf(ArchitectureReadinessAssessmentService.CHECK_TYPES);
        assertThat(result.path("checks").findValuesAsText("id")).doesNotHaveDuplicates();
        for (JsonNode check : result.path("checks")) {
            UUID.fromString(check.path("id").asText());
            assertThat(check.path("projectId").asText()).isEqualTo("p");
            assertThat(check.path("required").asBoolean()).isTrue();
            assertThat(check.path("status").asText()).isEqualTo("NOT_STARTED");
            assertThat(check.path("version").asLong()).isZero();
            for (String field : List.of("owner","plannedDate","actualDate","comment")) assertThat(check.path(field).isNull()).as(field).isTrue();
        }
        assertThat(result.at("/passport/version").asLong()).isEqualTo(1);
        assertThat(result.at("/passport/documentVersion").asText()).isEqualTo("1.0");
        assertThat(result.at("/project/version")).isEqualTo(before.at("/project/version"));
        assertThat(result.at("/passport/createdAt")).isEqualTo(before.at("/passport/createdAt"));
        assertThat(result.at("/passport/updatedAt")).isEqualTo(before.at("/passport/updatedAt"));
        assertThat(result.at("/assessment/status").asText()).isEqualTo("NOT_ASSESSED");
        assertThat(result.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        for (String field : List.of("components","integrations","risks")) assertThat(result.path(field).size()).isZero();
        assertThat(result.toString()).doesNotContain("budget");
        assertThat(getCard()).isEqualTo(result);
    }

    @Test
    void repeatAndPartialInitializationRefuseWithoutRepairingAnything() throws Exception {
        initialize(); var before = snapshot();
        assertThat(call(post(ROOT+"/checks/initialize"),"{\"expectedPassportVersion\":1}",409).path("code").asText()).isEqualTo("CHECKS_ALREADY_EXIST");
        assertVersion(call(post(ROOT+"/checks/initialize"),"{\"expectedPassportVersion\":0}",409),"PASSPORT",1);
        assertThat(snapshot()).isEqualTo(before);
        jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,version) values ('partial','q','THREAT_MODEL',true,'NOT_STARTED',0)");
        before = snapshot();
        assertThat(call(post("/api/projects/q/architecture/checks/initialize"),"{\"expectedPassportVersion\":0}",409).path("code").asText()).isEqualTo("CHECKS_ALREADY_EXIST");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from project_architecture_checks where project_id='q'",Long.class)).isEqualTo(1);
    }

    @Test
    void initializationUsesStrictBodyAndDoesNotExposeIndividualPost() throws Exception {
        var before = snapshot();
        for (String body : List.of("null","[]","1","{}","{","{\"expectedPassportVersion\":0,\"status\":\"PASSED\"}",
            "{\"expectedPassportVersion\":0,\"expectedPassportVersion\":0}","{\"expectedPassportVersion\":0} {}"," ".repeat(32769)))
            call(post(ROOT+"/checks/initialize"),body,400);
        for (String value : List.of("null","true","\"0\"","0.0","-1","9223372036854775807","9223372036854775808","[]"))
            call(post(ROOT+"/checks/initialize"),"{\"expectedPassportVersion\":"+value+"}",400);
        // An unmapped path can use MVC's 405 or the static-resource fallback's 404.
        assertThat(mvc.perform(post(ROOT+"/checks").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus()).isIn(404,405);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void partialPatchPreservesMissingValuesClearsNullableAndIncrementsIdenticalChangesOnce() throws Exception {
        JsonNode initialized = initialize(); String id = id(initialized,"ARCHITECTURE_APPROVAL");
        JsonNode first = change(id,0,"\"owner\":\" Zoé \"");
        assertThat(row(first,id).path("owner").asText()).isEqualTo("Zoé");
        JsonNode second = change(id,1,"\"plannedDate\":\"2026-10-20\",\"comment\":\" First\\r\\nSecond \"");
        assertThat(row(second,id).path("owner").asText()).isEqualTo("Zoé");
        assertThat(row(second,id).path("comment").asText()).isEqualTo("First\nSecond");
        JsonNode third = change(id,2,"\"owner\":null,\"plannedDate\":null,\"actualDate\":null,\"comment\":null");
        for (String field : List.of("owner","plannedDate","actualDate","comment")) assertThat(row(third,id).path(field).isNull()).isTrue();
        for (int version=3;version<5;version++) {
            JsonNode same = change(id,version,"\"required\":true,\"status\":\"NOT_STARTED\",\"owner\":null");
            assertThat(row(same,id).path("version").asLong()).isEqualTo(version+1);
            assertThat(same.at("/passport/version").asLong()).isEqualTo(1);
            assertThat(same.at("/project/version").asLong()).isEqualTo(1);
            assertThat(same.at("/passport/documentVersion").asText()).isEqualTo("1.0");
        }
        assertThat(getCard().at("/passport/updatedAt")).isEqualTo(initialized.at("/passport/updatedAt"));
    }

    @Test
    void validatesMergedStateAndRejectsContradictoryPatchesWithoutAnyChanges() throws Exception {
        String id = id(initialize(),"THREAT_MODEL"); var before = snapshot();
        for (String fields : List.of("\"required\":false","\"status\":\"NOT_REQUIRED\"","\"status\":\"PASSED\"",
            "\"actualDate\":\"2026-10-14\"","\"required\":false,\"status\":\"NOT_REQUIRED\"",
            "\"required\":false,\"status\":\"NOT_REQUIRED\",\"comment\":null",
            "\"required\":false,\"status\":\"PASSED\",\"actualDate\":\"2026-10-14\",\"comment\":\"Reason\""))
            call(patch(ROOT+"/checks/"+id),patchBody(0,fields),400);
        assertThat(snapshot()).isEqualTo(before);
        change(id,0,"\"status\":\"PASSED\",\"actualDate\":\"2026-10-14\"");
        JsonNode retained = change(id,1,"\"owner\":\"Owner\"");
        assertThat(row(retained,id).path("status").asText()).isEqualTo("PASSED");
        assertThat(row(retained,id).path("actualDate").asText()).isEqualTo("2026-10-14");
        before = snapshot();
        for (String fields : List.of("\"status\":\"BLOCKED\"","\"actualDate\":null",
            "\"required\":false,\"status\":\"NOT_REQUIRED\",\"comment\":\"Reason\""))
            call(patch(ROOT+"/checks/"+id),patchBody(2,fields),400);
        assertThat(snapshot()).isEqualTo(before);
        change(id,2,"\"required\":false,\"status\":\"NOT_REQUIRED\",\"actualDate\":null,\"comment\":\"Reason\"");
        before = snapshot();
        for (String fields : List.of("\"comment\":null","\"required\":true","\"status\":\"BLOCKED\""))
            call(patch(ROOT+"/checks/"+id),patchBody(3,fields),400);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode repaired = change(id,3,"\"required\":true,\"status\":\"BLOCKED\",\"comment\":null");
        assertThat(row(repaired,id).path("version").asLong()).isEqualTo(4);
    }

    @Test
    void strictPatchFieldsTypesDatesAndTextReuseExistingValidators() throws Exception {
        String id = id(initialize(),"ARCHITECTURE_APPROVAL"); var before = snapshot();
        for (String field : List.of("id","projectId","checkType","version","documentVersion","createdAt","updatedAt","expectedPassportVersion","assessment"))
            call(patch(ROOT+"/checks/"+id),patchBody(0,"\""+field+"\":null"),400);
        for (String value : List.of("null","\"true\"","0","[]","{}")) call(patch(ROOT+"/checks/"+id),patchBody(0,"\"required\":"+value),400);
        for (String value : List.of("null","\"passed\"","\" PASSED\"","true","[]","1","\"UNKNOWN\"")) call(patch(ROOT+"/checks/"+id),patchBody(0,"\"status\":"+value),400);
        for (String field : List.of("plannedDate","actualDate")) for (String value : List.of("\"2026-02-29\"","\"2026-02-30\"","\"15.10.2026\"","1","true","[]","{}"))
            call(patch(ROOT+"/checks/"+id),patchBody(0,"\""+field+"\":"+value),400);
        for (String field : List.of("owner","comment")) {
            for (String value : List.of(" ","x".repeat(field.equals("owner")?256:2001),"https://private.example","password=hidden","<script>bad</script>","bad\u0000text")) {
                JsonNode error = call(patch(ROOT+"/checks/"+id),json.createObjectNode().put("expectedVersion",0).put(field,value).toString(),400);
                assertThat(error.toString()).doesNotContain("private.example","hidden","<script>","java.","stackTrace");
            }
            for (String value : List.of("true","1","[]","{}")) call(patch(ROOT+"/checks/"+id),patchBody(0,"\""+field+"\":"+value),400);
        }
        for (String body : List.of("null","[]","{}","{\"expectedVersion\":0}","{\"expectedVersion\":0,\"owner\":\"One\",\"owner\":\"Two\"}",
            "{\"expectedVersion\":0,\"required\":true} {}"," ".repeat(32769))) call(patch(ROOT+"/checks/"+id),body,400);
        for (String value : List.of("null","true","\"0\"","0.0","-1","9223372036854775808"))
            call(patch(ROOT+"/checks/"+id),"{\"expectedVersion\":"+value+",\"owner\":\"Name\"}",400);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode accepted = call(patch(ROOT+"/checks/"+id),json.createObjectNode().put("expectedVersion",0).put("owner","e\u0301".repeat(255))
            .put("comment","x".repeat(2000)).put("plannedDate","2024-02-29").toString(),200);
        assertThat(row(accepted,id).path("owner").asText()).isEqualTo("é".repeat(255));
        assertThat(row(accepted,id).path("comment").asText()).hasSize(2000);
    }

    @Test
    void missingParentsForeignChecksAndStaleVersionsAreSafeAndDoNotWrite() throws Exception {
        for (String project : List.of("missing","no-passport")) {
            String code = project.equals("missing") ? "PROJECT_NOT_FOUND" : "PASSPORT_NOT_FOUND";
            assertThat(call(post("/api/projects/"+project+"/architecture/checks/initialize"),"{\"expectedPassportVersion\":0}",404).path("code").asText()).isEqualTo(code);
            assertThat(call(patch("/api/projects/"+project+"/architecture/checks/none"),patchBody(0,"\"owner\":\"Name\""),404).path("code").asText()).isEqualTo(code);
        }
        assertVersion(call(post(ROOT+"/checks/initialize"),"{\"expectedPassportVersion\":7}",409),"PASSPORT",0);
        JsonNode own = initialize();
        JsonNode foreign = call(post("/api/projects/q/architecture/checks/initialize"),"{\"expectedPassportVersion\":0}",201);
        var before = snapshot();
        for (String record : List.of("missing",id(foreign,"THREAT_MODEL"))) {
            JsonNode error = call(patch(ROOT+"/checks/"+record),patchBody(999,"\"owner\":\"Stolen\""),404);
            assertThat(error.path("code").asText()).isEqualTo("CHECK_NOT_FOUND"); assertThat(error.has("currentVersion")).isFalse();
        }
        assertVersion(call(patch(ROOT+"/checks/"+id(own,"THREAT_MODEL")),patchBody(5,"\"owner\":\"Changed\""),409),"CHECK",0);
        assertThat(snapshot()).isEqualTo(before);
        change(id(own,"THREAT_MODEL"),0,"\"owner\":\"Changed\""); before = snapshot();
        assertVersion(call(patch(ROOT+"/checks/"+id(own,"THREAT_MODEL")),patchBody(0,"\"owner\":\"Stale\""),409),"CHECK",1);
        assertThat(snapshot()).isEqualTo(before);
    }

    static Stream<Arguments> creationOrders() {
        return Stream.of(Arguments.of("initialize","initialize"),Arguments.of("initialize","components"),Arguments.of("components","initialize"),
            Arguments.of("initialize","integrations"),Arguments.of("integrations","initialize"));
    }
    @ParameterizedTest @MethodSource("creationOrders")
    void concurrentCreationSharesPassportVersionAndActuallyWaits(String firstAction, String secondAction) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode result = unchecked(post(actionPath(firstAction)),actionBody(firstAction),201);
                written.countDown(); await(release); return result;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(post(actionPath(secondAction)),actionBody(secondAction),409));
                blocked("projects",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertVersion(second.get(5,TimeUnit.SECONDS),"PASSPORT",1);
                JsonNode result = getCard();
                assertThat(result.path("checks").size()).isEqualTo(firstAction.equals("initialize")?10:0);
                assertThat(result.path("components").size()+result.path("integrations").size()).isEqualTo(firstAction.equals("initialize")?0:1);
                assertThat(result.at("/passport/version").asLong()).isEqualTo(1);
                assertThat(result.path("checks").findValuesAsText("checkType")).doesNotHaveDuplicates(); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @Test
    void concurrentPatchSameCheckWaitsAndUsesActualVersion() throws Exception {
        String id = id(initialize(),"THREAT_MODEL");
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode result = unchecked(patch(ROOT+"/checks/"+id),patchBody(0,"\"owner\":\"Winner\""),200);
                written.countDown(); await(release); return result;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(patch(ROOT+"/checks/"+id),patchBody(0,"\"owner\":\"Loser\""),409));
                blocked("project_architecture_checks",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertVersion(second.get(5,TimeUnit.SECONDS),"CHECK",1);
                JsonNode result = getCard(); assertThat(row(result,id).path("owner").asText()).isEqualTo("Winner");
                assertThat(result.at("/passport/version").asLong()).isEqualTo(1); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @Test
    void differentChecksCanFinishPatchBeforeFirstLockIsReleased() throws Exception {
        JsonNode initialized = initialize(); String one = id(initialized,"THREAT_MODEL"), two = id(initialized,"ACCESS_CONTROL");
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode result = unchecked(patch(ROOT+"/checks/"+one),patchBody(0,"\"owner\":\"First\""),200);
                written.countDown(); await(release); return result;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(patch(ROOT+"/checks/"+two),patchBody(0,"\"owner\":\"Second\""),200));
                JsonNode own = second.get(3,TimeUnit.SECONDS); assertThat(first.isDone()).isFalse();
                assertThat(row(own,two).path("version").asLong()).isEqualTo(1);
                assertThat(own.at("/passport/version").asLong()).isEqualTo(1);
                release.countDown(); first.get(5,TimeUnit.SECONDS);
                JsonNode result = getCard();
                assertThat(row(result,one).path("version").asLong()).isEqualTo(1); assertThat(row(result,two).path("version").asLong()).isEqualTo(1);
                assertThat(row(result,one).path("owner").asText()).isEqualTo("First"); assertThat(row(result,two).path("owner").asText()).isEqualTo("Second");
            } finally { release.countDown(); }
        }
    }

    @Test
    void failureAfterInitializationFlushRollsBackTenRowsAndPassportVersion() throws Exception {
        var before = snapshot();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from project_architecture_checks where project_id='p'",Long.class)).isEqualTo(10);
            assertThat(jdbc.queryForObject("select version from project_architecture_passports where project_id='p'",Long.class)).isEqualTo(1);
            throw new jakarta.persistence.PersistenceException("SQL secret internal.Class at C:\\private");
        }).when(readRepository).load(anyList());
        JsonNode error = call(post(ROOT+"/checks/initialize"),"{\"expectedPassportVersion\":0}",500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","internal.Class","C:");
        assertThat(snapshot()).isEqualTo(before); noOrphans();
        reset(readRepository); assertThat(initialize().path("checks").size()).isEqualTo(10);
    }

    @Test
    void failureAfterPatchFlushRollsBackFieldsDatesAndCheckVersion() throws Exception {
        String id = id(initialize(),"THREAT_MODEL"); var before = snapshot();
        doAnswer(call -> {
            assertThat(jdbc.queryForObject("select version from project_architecture_checks where id=?",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select status from project_architecture_checks where id=?",String.class,id)).isEqualTo("PASSED");
            throw new org.springframework.dao.DataAccessResourceFailureException("SQL secret at C:\\private");
        }).when(readRepository).load(anyList());
        JsonNode error = call(patch(ROOT+"/checks/"+id),patchBody(0,"\"status\":\"PASSED\",\"actualDate\":\"2026-10-14\",\"owner\":\"Changed\""),500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","C:");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void statusChangesRecalculateReadinessIncludingProductionAdmissionWithoutChangingFormula() throws Exception {
        JsonNode result = initialize();
        for (JsonNode check : result.path("checks"))
            change(check.path("id").asText(),0,"\"status\":\"PASSED\",\"actualDate\":\"2026-10-14\"");
        JsonNode ready = getCard(); assertThat(ready.at("/assessment/status").asText()).isEqualTo("READY");
        assertThat(ready.at("/assessment/statusLabel").asText()).isEqualTo("Архитектура и ИБ: проверки завершены");
        String normal = id(result,"ARCHITECTURE_APPROVAL"), production = id(result,"PRODUCTION_ADMISSION");
        JsonNode exempt = change(normal,1,"\"required\":false,\"status\":\"NOT_REQUIRED\",\"actualDate\":null,\"comment\":\"Not applicable\"");
        assertThat(exempt.at("/assessment/status").asText()).isEqualTo("READY");
        JsonNode blocked = change(normal,2,"\"required\":true,\"status\":\"BLOCKED\",\"comment\":null");
        assertThat(blocked.at("/assessment/status").asText()).isEqualTo("BLOCKED"); assertThat(codes(blocked)).contains("CHECK_BLOCKED","CHECKS_INCOMPLETE");
        JsonNode attention = change(normal,3,"\"status\":\"CHANGES_REQUIRED\"");
        assertThat(attention.at("/assessment/status").asText()).isEqualTo("ATTENTION_REQUIRED"); assertThat(codes(attention)).contains("CHECK_CHANGES_REQUIRED");
        JsonNode admission = change(production,1,"\"status\":\"CHANGES_REQUIRED\",\"actualDate\":null");
        assertThat(admission.at("/assessment/status").asText()).isEqualTo("BLOCKED");
        assertThat(codes(admission)).contains("PRODUCTION_ADMISSION_CHANGES_REQUIRED","CHECK_CHANGES_REQUIRED","CHECKS_INCOMPLETE");
        change(production,2,"\"required\":false,\"status\":\"NOT_REQUIRED\",\"comment\":\"Not applicable\"");
        JsonNode finalReady = change(normal,4,"\"status\":\"PASSED\",\"actualDate\":\"2026-10-14\"");
        assertThat(finalReady.at("/assessment/status").asText()).isEqualTo("READY");
        assertThat(finalReady.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        assertThat(finalReady.at("/passport/version").asLong()).isEqualTo(1); assertThat(finalReady.at("/project/version").asLong()).isEqualTo(1);
    }

    @Test
    void plannedDatesUseExistingOverdueAndUpcomingRules() throws Exception {
        String id = id(initialize(),"LOAD_TESTING");
        JsonNode yesterday = change(id,0,"\"plannedDate\":\"2026-10-14\"");
        assertThat(codes(yesterday)).contains("CHECK_OVERDUE"); assertThat(yesterday.at("/assessment/status").asText()).isEqualTo("ATTENTION_REQUIRED");
        int version=1;
        for (String date : List.of("2026-10-15","2026-10-29","2026-10-30")) {
            JsonNode result = change(id,version++,"\"plannedDate\":\""+date+"\"");
            assertThat(codes(result)).doesNotContain("CHECK_OVERDUE");
            assertThat(result.at("/assessment/upcomingChecks").size()).isEqualTo(date.equals("2026-10-30")?0:1);
            assertThat(result.at("/assessment/calculationDate").asText()).isEqualTo("2026-10-15");
            assertThat(result.at("/assessment/timeZone").asText()).isEqualTo("Europe/Moscow");
        }
    }

    @Test
    void responseSeesUncommittedInitializationInsideItsWriteTransaction() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Card result = new TransactionTemplate(manager).execute(status -> {
                Card own = mutations.initialize("p",json.createObjectNode().put("expectedPassportVersion",0).put("expectedPassportInstanceId",ArchitectureTestTokens.token(jdbc,"p")));
                assertThat(own.checks()).hasSize(10); assertThat(own.passport().version()).isEqualTo(1);
                try {
                    JsonNode external = executor.submit(() -> getCard()).get(5,TimeUnit.SECONDS);
                    assertThat(external.path("checks").size()).isZero(); assertThat(external.at("/passport/version").asLong()).isZero();
                } catch (Exception error) { throw new AssertionError(error); }
                return own;
            });
            assertThat(getCard()).isEqualTo(json.readTree(json.writeValueAsBytes(result)));
        }
    }

    @Test
    void getsDoNotInitializeAndPreserveEveryOtherEntityBeforeAndAfterMutations() throws Exception {
        seedOtherEntities(); var before = snapshot();
        repeatGets(); assertThat(snapshot()).isEqualTo(before);
        JsonNode initialized = initialize(); change(id(initialized,"THREAT_MODEL"),0,"\"owner\":\"Owner\",\"plannedDate\":\"2026-10-20\"");
        var after = snapshot();
        for (String table : TABLES) if (!List.of("project_architecture_checks","project_architecture_passports").contains(table))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        before.get("project_architecture_passports").stream().filter(row -> row.get("PROJECT_ID").equals("p")).forEach(row -> row.put("VERSION",1L));
        assertThat(after.get("project_architecture_passports")).isEqualTo(before.get("project_architecture_passports"));
        repeatGets(); assertThat(snapshot()).isEqualTo(after); noOrphans();
    }

    private void repeatGets() throws Exception {
        for (int repeat=0;repeat<3;repeat++) for (String path : List.of(ROOT,ROOT+"/deletion-impact","/api/projects/p/deletion-impact",
            "/api/architecture/portfolio","/api/projects/q/architecture","/api/projects/no-passport/architecture")) call(get(path),null,200);
    }
    private void seedOtherEntities() {
        jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) values ('card','p','backlog',0,'Task','Description','Owner','')");
        jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,position) values ('milestone','p','Gate','2026-10-01',false,0)");
        jdbc.update("insert into release_trains(id,name,status,cadence,quarter,planned_release_date,code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) values ('train','Train','planned','monthly','Q1','','','','',0,0,0,0,'Scope','Risk','Decision')");
        jdbc.update("insert into project_architecture_components(id,project_id,name,component_type,criticality,external_component,position,version) values ('component','p','Component','BACKEND','HIGH',false,0,7)");
        jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values ('integration','p','Exchange','Source','Target','OUTBOUND','API','NONE','LOW','READY',8)");
        jdbc.update("insert into project_integration_data_classes(integration_id,class_code) values ('integration','PUBLIC')");
        jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,version) values ('risk','p','Risk','DATA','LOW','LOW','MITIGATED',9)");
    }
    private JsonNode initialize() throws Exception { return call(post(ROOT+"/checks/initialize"),"{\"expectedPassportVersion\":0}",201); }
    private JsonNode change(String id, long version, String fields) throws Exception { return call(patch(ROOT+"/checks/"+id),patchBody(version,fields),200); }
    private static String patchBody(long version,String fields) { return "{\"expectedVersion\":"+version+","+fields+"}"; }
    private static String actionPath(String action) { return action.equals("initialize")?ROOT+"/checks/initialize":ROOT+"/"+action; }
    private String actionBody(String action) {
        ObjectNode body = json.createObjectNode().put("expectedPassportVersion",0);
        if (action.equals("initialize")) return body.toString();
        body.put("name","Name").put("criticality","HIGH");
        if (action.equals("components")) body.put("componentType","BACKEND").put("externalComponent",false).put("position",0);
        else { body.put("sourceSystem","Source").put("targetSystem","Target").put("direction","OUTBOUND").put("interactionType","API").put("authCategory","OAUTH2").put("readiness","READY"); body.putArray("dataClasses").add("PUBLIC"); }
        return body.toString();
    }
    private static String id(JsonNode card,String type) { for (JsonNode check : card.path("checks")) if (check.path("checkType").asText().equals(type)) return check.path("id").asText(); throw new AssertionError("Missing check"); }
    private static JsonNode row(JsonNode card,String id) { for (JsonNode check : card.path("checks")) if (check.path("id").asText().equals(id)) return check; throw new AssertionError("Missing check"); }
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
        assertThat(jdbc.queryForObject("select count(*) from project_architecture_checks c left join project_architecture_passports p on p.project_id=c.project_id where p.project_id is null",Long.class)).isZero();
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
