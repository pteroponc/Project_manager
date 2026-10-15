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
import project_manager.service.ArchitectureRiskMutationService;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_arch_risks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ArchitectureRiskMutationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ArchitectureRiskMutationService mutations;
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
    void createMinimalAndFullRiskReturnsSameAggregateAndServerVersions() throws Exception {
        var before = getCard();
        JsonNode minimal = create(); String id = lastId(minimal);
        UUID.fromString(id);
        JsonNode risk = row(minimal,id);
        assertThat(risk.path("version").asLong()).isZero();
        assertThat(risk.path("projectId").asText()).isEqualTo("p");
        for (String field : List.of("description","owner","treatmentDueDate","treatmentPlan","closedDate","acceptanceJustification","acceptanceVerification"))
            assertThat(risk.path(field).isNull()).as(field).isTrue();
        assertThat(risk.path("level").asText()).isEqualTo("LOW");
        assertThat(minimal.at("/passport/version").asLong()).isEqualTo(1);
        assertThat(minimal.at("/passport/documentVersion").asText()).isEqualTo("1.0");
        assertThat(minimal.at("/project")).isEqualTo(before.at("/project"));
        for (String field : List.of("createdAt","updatedAt")) assertThat(minimal.path("passport").path(field)).isEqualTo(before.path("passport").path(field));
        for (String field : List.of("components","integrations","checks")) assertThat(minimal.path(field).size()).isZero();
        assertThat(getCard()).isEqualTo(minimal);
        ObjectNode body = riskBody(1).put("name"," Zoé ").put("description"," First\r\nSecond ").put("owner","Owner")
            .put("treatmentDueDate","2024-02-29").put("treatmentPlan","Plan").put("status","CLOSED")
            .put("closedDate","2099-12-31").put("acceptanceJustification","Past decision");
        JsonNode full = call(post(ROOT+"/risks"),body.toString(),201);
        JsonNode added = full.path("risks").get(0).path("id").asText().equals(id) ? full.path("risks").get(1) : full.path("risks").get(0);
        assertThat(added.path("name").asText()).isEqualTo("Zoé");
        assertThat(added.path("description").asText()).isEqualTo("First\nSecond");
        assertThat(added.path("closedDate").asText()).isEqualTo("2099-12-31");
        assertThat(added.path("acceptanceJustification").asText()).isEqualTo("Past decision");
        assertThat(added.path("version").asLong()).isZero();
        assertThat(full.at("/passport/version").asLong()).isEqualTo(2);
        assertThat(full.toString()).doesNotContain("budget");
        assertThat(getCard()).isEqualTo(full); noOrphans();
    }

    @Test
    void partialPatchPreservesOmittedClearsNullableAndAdvancesNoOpOnce() throws Exception {
        ObjectNode body = riskBody(0).put("description","Description").put("owner","Owner").put("treatmentPlan","Plan")
            .put("treatmentDueDate","2026-10-01").put("acceptanceJustification","Optional decision");
        JsonNode created = call(post(ROOT+"/risks"),body.toString(),201); String id = lastId(created);
        JsonNode first = change(id,0,"\"name\":\" Renamed \",\"category\":\"OTHER\"");
        JsonNode state = row(first,id);
        for (String field : List.of("description","owner","treatmentDueDate","treatmentPlan","status","acceptanceJustification"))
            assertThat(state.path(field)).as(field).isEqualTo(row(created,id).path(field));
        JsonNode cleared = change(id,1,"\"description\":null,\"owner\":null,\"treatmentDueDate\":null,\"treatmentPlan\":null,\"closedDate\":null,\"acceptanceJustification\":null");
        for (String field : List.of("description","owner","treatmentDueDate","treatmentPlan","closedDate","acceptanceJustification"))
            assertThat(row(cleared,id).path(field).isNull()).as(field).isTrue();
        for (int version=2;version<4;version++) {
            JsonNode same = change(id,version,"\"status\":\"OPEN\",\"owner\":null");
            assertThat(row(same,id).path("version").asLong()).isEqualTo(version+1);
            assertThat(same.at("/passport/version").asLong()).isEqualTo(1);
            assertThat(same.at("/project/version").asLong()).isEqualTo(1);
            assertThat(same.at("/passport/updatedAt")).isEqualTo(created.at("/passport/updatedAt"));
        }
    }

    @Test
    void acceptedAndClosedTransitionsValidateMergedStateBeforeAnyMutation() throws Exception {
        String id = lastId(create()); var before = snapshot();
        for (String fields : List.of("\"status\":\"ACCEPTED\"","\"status\":\"CLOSED\"","\"closedDate\":\"2026-10-14\""))
            call(patch(ROOT+"/risks/"+id),patchBody(0,fields),400);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode accepted = change(id,0,"\"status\":\"ACCEPTED\",\"acceptanceJustification\":\"Decision\"");
        assertThat(row(accepted,id).path("acceptanceVerification").asText()).isEqualTo("UNVERIFIED");
        before = snapshot();
        call(patch(ROOT+"/risks/"+id),patchBody(1,"\"name\":\"Must rollback\",\"acceptanceJustification\":null"),400);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode preserved = change(id,1,"\"owner\":\"Owner\"");
        assertThat(row(preserved,id).path("acceptanceJustification").asText()).isEqualTo("Decision");
        JsonNode opened = change(id,2,"\"status\":\"OPEN\"");
        assertThat(row(opened,id).path("acceptanceJustification").asText()).isEqualTo("Decision");
        assertThat(row(opened,id).path("acceptanceVerification").isNull()).isTrue();
        change(id,3,"\"status\":\"CLOSED\",\"closedDate\":\"2099-12-31\"");
        JsonNode retained = change(id,4,"\"description\":\"Changed\"");
        assertThat(row(retained,id).path("closedDate").asText()).isEqualTo("2099-12-31");
        before = snapshot();
        for (String fields : List.of("\"status\":\"IN_PROGRESS\"","\"closedDate\":null","\"status\":\"ACCEPTED\""))
            call(patch(ROOT+"/risks/"+id),patchBody(5,fields),400);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode reopened = change(id,5,"\"status\":\"ACCEPTED\",\"closedDate\":null");
        assertThat(row(reopened,id).path("acceptanceJustification").asText()).isEqualTo("Decision");
        assertThat(row(reopened,id).path("version").asLong()).isEqualTo(6);
        before = snapshot();
        for (ObjectNode invalid : List.of(riskBody(1).put("status","ACCEPTED"),riskBody(1).put("status","CLOSED"),riskBody(1).put("closedDate","2026-10-15")))
            call(post(ROOT+"/risks"),invalid.toString(),400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsUnknownImmutableFieldsStrictJsonWrongTypesAndMissingRequiredFields() throws Exception {
        var before = snapshot();
        for (String field : List.of("name","category","probability","impact","status")) {
            ObjectNode omitted = riskBody(0); omitted.remove(field); call(post(ROOT+"/risks"),omitted.toString(),400);
            call(post(ROOT+"/risks"),riskBody(0).putNull(field).toString(),400);
        }
        assertThat(snapshot()).isEqualTo(before);
        String id = lastId(create()); before = snapshot();
        for (boolean postRequest : List.of(true,false)) {
            String versionField = postRequest?"expectedPassportVersion":"expectedVersion";
            for (String field : List.of("id","projectId","version","level","acceptanceVerification","createdAt","updatedAt","assessment","documentVersion")) {
                ObjectNode body = postRequest?riskBody(1):json.createObjectNode().put("expectedVersion",0);
                body.putNull(field); call(postRequest?post(ROOT+"/risks"):patch(ROOT+"/risks/"+id),body.toString(),400);
            }
            for (String body : List.of("null","[]","1","{}","{"," ".repeat(32769),
                "{\""+versionField+"\":0,\"name\":\"First\",\"name\":\"Second\"}",
                "{\""+versionField+"\":0,\"name\":\"First\"} {}"))
                call(postRequest?post(ROOT+"/risks"):patch(ROOT+"/risks/"+id),body,400);
            for (String value : List.of("null","true","\"0\"","0.0","-1","9223372036854775807","9223372036854775808","[]")) {
                ObjectNode body = postRequest?riskBody(1):json.createObjectNode().put("expectedVersion",0).put("name","Name");
                body.set(versionField,json.readTree(value));
                call(postRequest?post(ROOT+"/risks"):patch(ROOT+"/risks/"+id),body.toString(),400);
            }
        }
        call(patch(ROOT+"/risks/"+id),"{\"expectedVersion\":0}",400);
        for (String field : List.of("name","category","probability","impact","status"))
            for (String value : List.of("null","true","1","[]","{}","\"unknown\"","\" OPEN\"")) {
                if (field.equals("name") && value.startsWith("\"")) continue;
                call(patch(ROOT+"/risks/"+id),patchBody(0,"\""+field+"\":"+value),400);
            }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void textLimitsNullableTypesAndCalendarValidationReuseExistingRules() throws Exception {
        String id = lastId(create()); var before = snapshot();
        for (String field : List.of("name","owner","description","treatmentPlan","acceptanceJustification")) {
            int limit = List.of("name","owner").contains(field)?255:2000;
            for (String value : List.of(" ","x".repeat(limit+1),"<script>bad</script>","https://private.example","token=hidden","192.168.0.1","bad\u0000text")) {
                JsonNode error = call(patch(ROOT+"/risks/"+id),json.createObjectNode().put("expectedVersion",0).put(field,value).toString(),400);
                assertThat(error.toString()).doesNotContain("private.example","hidden","<script>","java.","stackTrace");
            }
            for (String value : List.of("true","1","[]","{}")) call(patch(ROOT+"/risks/"+id),patchBody(0,"\""+field+"\":"+value),400);
        }
        for (String field : List.of("closedDate","treatmentDueDate"))
            for (String value : List.of("\"2026-02-29\"","\"2026-02-30\"","\"15.10.2026\"","\"2026-1-01\"","1","true","[]","{}"))
                call(patch(ROOT+"/risks/"+id),patchBody(0,"\""+field+"\":"+value),400);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode valid = call(patch(ROOT+"/risks/"+id),json.createObjectNode().put("expectedVersion",0).put("name","e\u0301".repeat(255))
            .put("owner","x".repeat(255)).put("description","x".repeat(2000)).put("treatmentPlan","x".repeat(2000))
            .put("acceptanceJustification","x".repeat(2000)).put("treatmentDueDate","2024-02-29").toString(),200);
        assertThat(row(valid,id).path("name").asText()).isEqualTo("é".repeat(255));
        assertThat(row(valid,id).path("description").asText()).hasSize(2000);
    }

    @Test
    void missingParentsForeignRisksAndStaleVersionsAreSafeAndDoNotWrite() throws Exception {
        for (String project : List.of("missing","no-passport")) {
            String code = project.equals("missing")?"PROJECT_NOT_FOUND":"PASSPORT_NOT_FOUND";
            assertThat(call(post("/api/projects/"+project+"/architecture/risks"),riskBody(0).toString(),404).path("code").asText()).isEqualTo(code);
            assertThat(call(patch("/api/projects/"+project+"/architecture/risks/none"),patchBody(0,"\"owner\":\"Name\""),404).path("code").asText()).isEqualTo(code);
        }
        assertVersion(call(post(ROOT+"/risks"),riskBody(5).toString(),409),"PASSPORT",0);
        String own = lastId(create());
        String foreign = lastId(call(post("/api/projects/q/architecture/risks"),riskBody(0).toString(),201));
        var before = snapshot();
        for (String record : List.of("missing",foreign)) {
            JsonNode error = call(patch(ROOT+"/risks/"+record),patchBody(999,"\"name\":\"Stolen\""),404);
            assertThat(error.path("code").asText()).isEqualTo("RISK_NOT_FOUND"); assertThat(error.has("currentVersion")).isFalse();
        }
        assertVersion(call(patch(ROOT+"/risks/"+own),patchBody(5,"\"name\":\"Changed\""),409),"RISK",0);
        assertVersion(call(post(ROOT+"/risks"),riskBody(0).toString(),409),"PASSPORT",1);
        assertThat(snapshot()).isEqualTo(before);
        change(own,0,"\"name\":\"Changed\""); before = snapshot();
        assertVersion(call(patch(ROOT+"/risks/"+own),patchBody(0,"\"name\":\"Stale\""),409),"RISK",1);
        assertThat(snapshot()).isEqualTo(before);
    }

    static Stream<Arguments> riskMatrix() {
        String[] levels = {"LOW","MEDIUM","HIGH","CRITICAL"};
        String[][] expected = {{"LOW","LOW","MEDIUM","HIGH"},{"LOW","MEDIUM","HIGH","CRITICAL"},
            {"MEDIUM","HIGH","CRITICAL","CRITICAL"},{"HIGH","CRITICAL","CRITICAL","CRITICAL"}};
        var cases = Stream.<Arguments>builder();
        for (int p=0;p<4;p++) for (int i=0;i<4;i++) cases.add(Arguments.of(levels[p],levels[i],expected[p][i]));
        return cases.build();
    }
    @ParameterizedTest @MethodSource("riskMatrix")
    void riskLevelAndReadinessComeFromExistingMatrix(String probability,String impact,String level) throws Exception {
        seedPassedChecks();
        JsonNode created = call(post(ROOT+"/risks"),riskBody(0).put("probability",probability).put("impact",impact).toString(),201);
        assertThat(row(created,lastId(created)).path("level").asText()).isEqualTo(level);
        assertThat(created.at("/assessment/status").asText()).isEqualTo(level.equals("CRITICAL")?"BLOCKED":level.equals("HIGH")?"ATTENTION_REQUIRED":"READY");
        assertThat(created.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
    }

    @Test
    void statusTransitionsRetainAllReasonsAndAcceptedRisksWithoutCertifyingApproval() throws Exception {
        seedPassedChecks();
        JsonNode card = call(post(ROOT+"/risks"),riskBody(0).put("probability","HIGH").put("impact","HIGH").toString(),201);
        String id = lastId(card);
        assertThat(codes(card)).contains("RISK_CRITICAL_OPEN");
        JsonNode progress = change(id,0,"\"status\":\"IN_PROGRESS\"");
        assertThat(progress.at("/assessment/status").asText()).isEqualTo("BLOCKED");
        JsonNode high = change(id,1,"\"probability\":\"LOW\",\"impact\":\"CRITICAL\"");
        assertThat(high.at("/assessment/status").asText()).isEqualTo("ATTENTION_REQUIRED"); assertThat(codes(high)).contains("RISK_HIGH_OPEN");
        JsonNode accepted = change(id,2,"\"status\":\"ACCEPTED\",\"acceptanceJustification\":\"Management decision\"");
        assertAccepted(accepted,id,"HIGH");
        JsonNode critical = change(id,3,"\"probability\":\"CRITICAL\""); assertAccepted(critical,id,"CRITICAL");
        for (String status : List.of("MITIGATED","CLOSED")) {
            JsonNode changed = change(id,status.equals("MITIGATED")?4:5,"\"status\":\""+status+"\""+(status.equals("CLOSED")?",\"closedDate\":\"2026-10-14\"":""));
            assertThat(changed.at("/assessment/status").asText()).isEqualTo("READY");
            assertThat(changed.at("/assessment/acceptedHighRisks").size()).isZero();
            assertThat(row(changed,id).path("acceptanceJustification").asText()).isEqualTo("Management decision");
        }
        change(id,6,"\"status\":\"ACCEPTED\",\"closedDate\":null");
        JsonNode other = call(post(ROOT+"/risks"),riskBody(1).put("probability","HIGH").put("impact","HIGH").toString(),201);
        assertThat(other.at("/assessment/status").asText()).isEqualTo("BLOCKED");
        assertThat(codes(other)).contains("ACCEPTED_HIGH_RISK","RISK_CRITICAL_OPEN");
        assertThat(other.at("/assessment/acceptedHighRisks").size()).isEqualTo(1);
        jdbc.update("update project_architecture_passports set stage='CHANGES_REQUIRED' where project_id='p'");
        jdbc.update("update project_architecture_checks set status='NOT_STARTED',actual_date=null where id='check-0'");
        JsonNode all = getCard(); assertThat(codes(all)).containsAll(codes(other)).contains("CHECKS_INCOMPLETE");
        assertThat(all.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        assertThat(all.at("/passport/documentVersion").asText()).isEqualTo("1.0");
    }

    static Stream<Arguments> creationOrders() {
        return Stream.of(Arguments.of("risks","risks"),Arguments.of("risks","components"),Arguments.of("components","risks"),
            Arguments.of("risks","integrations"),Arguments.of("integrations","risks"),
            Arguments.of("risks","initialize"),Arguments.of("initialize","risks"));
    }
    @ParameterizedTest @MethodSource("creationOrders")
    void concurrentCreationSharesPassportVersionAndActuallyWaits(String firstAction,String secondAction) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = unchecked(post(actionPath(firstAction)),actionBody(firstAction),201);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(post(actionPath(secondAction)),actionBody(secondAction),409));
                blocked("projects",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertVersion(second.get(5,TimeUnit.SECONDS),"PASSPORT",1);
                JsonNode result = getCard();
                assertThat(result.path("risks").size()).isEqualTo(firstAction.equals("risks")?1:0);
                assertThat(result.path("components").size()).isEqualTo(firstAction.equals("components")?1:0);
                assertThat(result.path("integrations").size()).isEqualTo(firstAction.equals("integrations")?1:0);
                assertThat(result.path("checks").size()).isEqualTo(firstAction.equals("initialize")?10:0);
                assertThat(result.at("/passport/version").asLong()).isEqualTo(1); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @Test
    void sameRiskPatchActuallyWaitsAndReturnsRealCurrentVersion() throws Exception {
        String id = lastId(create());
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = unchecked(patch(ROOT+"/risks/"+id),patchBody(0,"\"name\":\"Winner\""),200);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(patch(ROOT+"/risks/"+id),patchBody(0,"\"name\":\"Loser\""),409));
                blocked("project_security_risks",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertVersion(second.get(5,TimeUnit.SECONDS),"RISK",1);
                JsonNode result = getCard(); assertThat(row(result,id).path("name").asText()).isEqualTo("Winner");
                assertThat(row(result,id).path("version").asLong()).isEqualTo(1);
                assertThat(result.at("/passport/version").asLong()).isEqualTo(1); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @Test
    void differentRisksCanFinishBeforeFirstTransactionReleasesLock() throws Exception {
        String one = lastId(create());
        JsonNode created = call(post(ROOT+"/risks"),riskBody(1).toString(),201);
        String two = created.path("risks").get(0).path("id").asText().equals(one)?created.path("risks").get(1).path("id").asText():created.path("risks").get(0).path("id").asText();
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode own = unchecked(patch(ROOT+"/risks/"+one),patchBody(0,"\"name\":\"First\""),200);
                written.countDown(); await(release); return own;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(patch(ROOT+"/risks/"+two),patchBody(0,"\"name\":\"Second\""),200));
                JsonNode own = second.get(3,TimeUnit.SECONDS); assertThat(first.isDone()).isFalse();
                assertThat(row(own,two).path("version").asLong()).isEqualTo(1);
                release.countDown(); first.get(5,TimeUnit.SECONDS);
                JsonNode result = getCard();
                assertThat(row(result,one).path("version").asLong()).isEqualTo(1); assertThat(row(result,two).path("version").asLong()).isEqualTo(1);
                assertThat(row(result,one).path("name").asText()).isEqualTo("First"); assertThat(row(result,two).path("name").asText()).isEqualTo("Second");
                assertThat(result.at("/passport/version").asLong()).isEqualTo(2); assertThat(result.at("/project/version").asLong()).isEqualTo(1);
            } finally { release.countDown(); }
        }
    }

    @Test
    void failureAfterCreateFlushRollsBackRiskAndPassportCounter() throws Exception {
        seedOtherEntities(); var before = snapshot();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from project_security_risks where project_id='p'",Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select version from project_architecture_passports where project_id='p'",Long.class)).isEqualTo(1);
            throw new jakarta.persistence.PersistenceException("SQL secret internal.Class at C:\\private");
        }).when(readRepository).load(anyList());
        JsonNode error = call(post(ROOT+"/risks"),riskBody(0).toString(),500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","internal.Class","C:");
        assertThat(snapshot()).isEqualTo(before); noOrphans();
        reset(readRepository); create();
        assertThat(jdbc.queryForObject("select count(*) from project_security_risks",Long.class)).isEqualTo(2);
    }

    @Test
    void failureAfterPatchFlushRollsBackAllFieldsAndVersion() throws Exception {
        String id = lastId(create()); var before = snapshot();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(jdbc.queryForObject("select version from project_security_risks where id=?",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select status from project_security_risks where id=?",String.class,id)).isEqualTo("CLOSED");
            throw new org.springframework.dao.DataAccessResourceFailureException("SQL secret at C:\\private");
        }).when(readRepository).load(anyList());
        JsonNode error = call(patch(ROOT+"/risks/"+id),patchBody(0,"\"status\":\"CLOSED\",\"closedDate\":\"2026-10-14\",\"name\":\"Changed\",\"owner\":\"Owner\""),500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","C:");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void responseReflectsOwnUncommittedWriteAndGetPreservesEverythingElse() throws Exception {
        seedOtherEntities(); seedPassedChecks(); var before = snapshot();
        repeatGets(); assertThat(snapshot()).isEqualTo(before);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Card own = new TransactionTemplate(manager).execute(status -> {
                Card card = mutations.create("p",riskBody(0).put("expectedPassportInstanceId",ArchitectureTestTokens.token(jdbc,"p")));
                assertThat(card.risks()).hasSize(2); assertThat(card.passport().version()).isEqualTo(1);
                try {
                    JsonNode external = executor.submit(() -> getCard()).get(5,TimeUnit.SECONDS);
                    assertThat(external.path("risks").size()).isEqualTo(1); assertThat(external.at("/passport/version").asLong()).isZero();
                } catch (Exception error) { throw new AssertionError(error); }
                return card;
            });
            assertThat(getCard()).isEqualTo(json.readTree(json.writeValueAsBytes(own)));
        }
        String id = getCard().path("risks").findValuesAsText("id").stream().filter(value -> !value.equals("risk")).findFirst().orElseThrow();
        change(id,0,"\"owner\":\"Owner\""); var after = snapshot();
        for (String table : TABLES) if (!List.of("project_security_risks","project_architecture_passports").contains(table))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        before.get("project_architecture_passports").stream().filter(row -> row.get("PROJECT_ID").equals("p")).forEach(row -> row.put("VERSION",1L));
        assertThat(after.get("project_architecture_passports")).isEqualTo(before.get("project_architecture_passports"));
        assertThat(after.get("project_security_risks").stream().filter(row -> row.get("ID").equals("risk")).toList()).isEqualTo(before.get("project_security_risks"));
        repeatGets(); assertThat(snapshot()).isEqualTo(after); noOrphans();
    }

    private static String actionPath(String action) { return action.equals("initialize")?ROOT+"/checks/initialize":ROOT+"/"+action; }
    private String actionBody(String action) {
        if (action.equals("risks")) return riskBody(0).toString();
        ObjectNode body = json.createObjectNode().put("expectedPassportVersion",0);
        if (action.equals("initialize")) return body.toString();
        body.put("name","Name").put("criticality","HIGH");
        if (action.equals("components")) body.put("componentType","BACKEND").put("externalComponent",false).put("position",0);
        else { body.put("sourceSystem","Source").put("targetSystem","Target").put("direction","OUTBOUND").put("interactionType","API").put("authCategory","OAUTH2").put("readiness","READY"); body.putArray("dataClasses").add("PUBLIC"); }
        return body.toString();
    }

    private void assertAccepted(JsonNode card,String id,String level) {
        assertThat(card.at("/assessment/status").asText()).isEqualTo("ATTENTION_REQUIRED");
        assertThat(codes(card)).contains("ACCEPTED_HIGH_RISK");
        assertThat(row(card,id).path("acceptanceVerification").asText()).isEqualTo("UNVERIFIED");
        JsonNode accepted = card.at("/assessment/acceptedHighRisks/0");
        assertThat(accepted.path("id").asText()).isEqualTo(id); assertThat(accepted.path("level").asText()).isEqualTo(level);
        assertThat(accepted.path("justification").asText()).isEqualTo("Management decision");
        assertThat(accepted.path("acceptanceVerification").asText()).isEqualTo("UNVERIFIED");
    }
    private void seedPassedChecks() {
        int i=0;
        for (String type : ArchitectureReadinessAssessmentService.CHECK_TYPES)
            jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,actual_date,version) values (?,'p',?,true,'PASSED','2026-10-14',0)","check-"+i++,type);
    }
    private ObjectNode riskBody(long version) {
        return json.createObjectNode().put("expectedPassportVersion",version).put("name","Risk").put("category","DATA")
            .put("probability","LOW").put("impact","LOW").put("status","OPEN");
    }
    private JsonNode create() throws Exception { return call(post(ROOT+"/risks"),riskBody(0).toString(),201); }
    private static String lastId(JsonNode card) { return card.path("risks").get(card.path("risks").size()-1).path("id").asText(); }
    private static JsonNode row(JsonNode card,String id) { for (JsonNode risk : card.path("risks")) if (risk.path("id").asText().equals(id)) return risk; throw new AssertionError("Missing risk"); }
    private JsonNode change(String id,long version,String fields) throws Exception { return call(patch(ROOT+"/risks/"+id),patchBody(version,fields),200); }
    private static String patchBody(long version,String fields) { return "{\"expectedVersion\":"+version+","+fields+"}"; }
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
        assertThat(jdbc.queryForObject("select count(*) from project_security_risks c left join project_architecture_passports p on p.project_id=c.project_id where p.project_id is null",Long.class)).isZero();
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
