package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.support.TransactionTemplate;
import project_manager.service.ArchitectureChildMutationService;
import project_manager.service.ArchitectureIntegrationClassWriter;
import project_manager.service.ProjectWriteLock;
import project_manager.web.dto.ArchitectureReadDtos.Card;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** HTTP and transaction tests exclusively on this disposable memory database. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_arch_children;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ArchitectureChildMutationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired ArchitectureChildMutationService mutations;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean ArchitectureIntegrationClassWriter classWriter;
    @MockitoSpyBean ProjectWriteLock projectLock;
    @TestBean(name = "architectureAssessmentClock", methodName = "fixedClock", enforceOverride = true) Clock clock;

    private static final List<String> TABLES = List.of("project_integration_data_classes", "project_architecture_components",
        "project_architecture_integrations", "project_architecture_checks", "project_security_risks", "project_architecture_data_classes",
        "project_architecture_environments", "project_architecture_passports", "project_milestones", "board_cards", "release_trains", "projects");
    static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"),ZoneOffset.UTC); }

    @BeforeEach
    void resetState() {
        reset(classWriter, projectLock);
        TABLES.forEach(table -> jdbc.update("delete from " + table));
        for (String id : List.of("p", "q", "no-passport")) {
            jdbc.update("insert into projects(id,name,owner,status,health,budget,progress,quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,version) "
                + "values (?,?,'Owner','active','green',2147483647,0,'Q1','','','','','','','Summary',1)",id,id);
            if (!id.equals("no-passport")) jdbc.update("insert into project_architecture_passports(instance_id,project_id,criticality,stage,solution_type,version,created_at,updated_at) "
                + "values (random_uuid(),?,'HIGH','NOT_STARTED','NEW_SOLUTION',0,'2026-01-01T00:00:00Z','2026-02-01T00:00:00Z')",id);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void createAndPartialPatchReturnCompleteAggregateWithSeparateDocumentVersion(String kind) throws Exception {
        JsonNode before = getCard();
        JsonNode created = call(post(path("p",kind)),createBody(kind,0).toString(),201);
        String id = created.path(kind).get(0).path("id").asText(); UUID.fromString(id);
        assertThat(created.at("/passport/version").asLong()).isEqualTo(1);
        assertThat(created.at("/passport/documentVersion").asText()).isEqualTo("1.0");
        assertThat(created.at("/project/version").asLong()).isEqualTo(1);
        assertThat(created.at("/passport/createdAt")).isEqualTo(before.at("/passport/createdAt"));
        assertThat(created.at("/passport/updatedAt")).isEqualTo(before.at("/passport/updatedAt"));
        assertThat(row(created,kind,id).path("version").asLong()).isZero();
        assertThat(row(created,kind,id).path("projectId").asText()).isEqualTo("p");
        assertThat(getCard()).isEqualTo(created);
        JsonNode result = call(patch(path("p",kind)+"/"+id),"{\"expectedVersion\":0,\"name\":\" Updated \"}",200);
        JsonNode expected = row(created,kind,id).deepCopy();
        ((ObjectNode)expected).put("name","Updated").put("version",1);
        assertThat(row(result,kind,id)).isEqualTo(expected);
        assertThat(result.at("/passport/version").asLong()).isEqualTo(1);
        assertThat(result.at("/passport/documentVersion").asText()).isEqualTo("1.0");
        assertThat(result.at("/project/version").asLong()).isEqualTo(1);
        assertThat(result.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        assertThat(result.toString()).doesNotContain("budget");
        assertThat(result.path("checks").size()).isZero(); assertThat(result.path("risks").size()).isZero();
        assertThat(getCard()).isEqualTo(result);
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void nullableAndOmittedFieldsAndIdenticalPatchesKeepTheirSemantics(String kind) throws Exception {
        List<String> nullable = kind.equals("components") ? List.of("purpose","platform") : List.of("responsibleParty","comment");
        ObjectNode minimal = createBody(kind,0); nullable.forEach(minimal::remove);
        JsonNode created = call(post(path("p",kind)),minimal.toString(),201);
        String id = created.path(kind).get(0).path("id").asText();
        for (String field : nullable) assertThat(row(created,kind,id).path(field).isNull()).isTrue();
        ObjectNode fields = json.createObjectNode().put("expectedVersion",0);
        nullable.forEach(field -> fields.put(field,"Value"));
        JsonNode filled = call(patch(path("p",kind)+"/"+id),fields.toString(),200);
        for (String field : nullable) assertThat(row(filled,kind,id).path(field).asText()).isEqualTo("Value");
        fields.put("expectedVersion",1); nullable.forEach(fields::putNull);
        JsonNode cleared = call(patch(path("p",kind)+"/"+id),fields.toString(),200);
        for (String field : nullable) assertThat(row(cleared,kind,id).path(field).isNull()).isTrue();
        for (int version = 2; version < 4; version++) {
            ObjectNode same = json.createObjectNode().put("expectedVersion",version).put("name","Name");
            if (kind.equals("integrations")) same.putArray("dataClasses").add("PUBLIC").add("INTERNAL");
            JsonNode result = call(patch(path("p",kind)+"/"+id),same.toString(),200);
            assertThat(row(result,kind,id).path("version").asLong()).isEqualTo(version+1);
            assertThat(result.at("/passport/version").asLong()).isEqualTo(1);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void missingParentsRecordsAndForeignRecordsReturn404WithoutWrites(String kind) throws Exception {
        for (String project : List.of("missing", "no-passport")) {
            String code = project.equals("missing") ? "PROJECT_NOT_FOUND" : "PASSPORT_NOT_FOUND";
            assertThat(call(post(path(project,kind)),createBody(kind,0).toString(),404).path("code").asText()).isEqualTo(code);
            assertThat(call(patch(path(project,kind)+"/missing"),"{\"expectedVersion\":0,\"name\":\"New\"}",404).path("code").asText()).isEqualTo(code);
        }
        JsonNode foreign = call(post(path("q",kind)),createBody(kind,0).toString(),201);
        String id = foreign.path(kind).get(0).path("id").asText(); var before = snapshot();
        for (String recordId : List.of("missing",id)) {
            JsonNode error = call(patch(path("p",kind)+"/"+recordId),"{\"expectedVersion\":999,\"name\":\"Stolen\"}",404);
            assertThat(error.path("code").asText()).isEqualTo(kind.equals("components") ? "COMPONENT_NOT_FOUND" : "INTEGRATION_NOT_FOUND");
            assertThat(error.has("currentVersion")).isFalse();
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void staleVersionsIdentifyTheRealResourceAndChangeNothing(String kind) throws Exception {
        var before = snapshot();
        JsonNode error = call(post(path("p",kind)),createBody(kind,7).toString(),409);
        assertVersion(error,"PASSPORT",0); assertThat(snapshot()).isEqualTo(before);
        JsonNode created = call(post(path("p",kind)),createBody(kind,0).toString(),201);
        String id = created.path(kind).get(0).path("id").asText(); before = snapshot();
        assertVersion(call(post(path("p",kind)),createBody(kind,0).toString(),409),"PASSPORT",1);
        assertVersion(call(patch(path("p",kind)+"/"+id),"{\"expectedVersion\":7,\"name\":\"New\"}",409),resource(kind),0);
        assertThat(snapshot()).isEqualTo(before);
        call(patch(path("p",kind)+"/"+id),"{\"expectedVersion\":0,\"name\":\"New\"}",200); before = snapshot();
        assertVersion(call(patch(path("p",kind)+"/"+id),"{\"expectedVersion\":0,\"name\":\"Old\"}",409),resource(kind),1);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void validatesRequiredFieldsTypesNullEnumsLengthsAndStrictJsonBeforeWriting(String kind) throws Exception {
        ObjectNode valid = createBody(kind,0); var before = snapshot();
        Set<String> nullable = kind.equals("components") ? Set.of("purpose","platform") : Set.of("responsibleParty","comment");
        for (String field : fieldNames(valid)) {
            if (nullable.contains(field)) continue;
            ObjectNode bad = valid.deepCopy(); bad.remove(field); call(post(path("p",kind)),bad.toString(),400);
            bad = valid.deepCopy(); bad.putNull(field); call(post(path("p",kind)),bad.toString(),400);
            bad = valid.deepCopy(); bad.putArray(field).add("WRONG"); call(post(path("p",kind)),bad.toString(),400);
        }
        List<String> enumFields = kind.equals("components") ? List.of("criticality","componentType")
            : List.of("criticality","direction","interactionType","authCategory","readiness");
        for (String field : enumFields) for (String value : List.of("unknown",valid.path(field).asText().toLowerCase()," " + valid.path(field).asText())) {
            ObjectNode bad = valid.deepCopy(); bad.put(field,value); call(post(path("p",kind)),bad.toString(),400);
        }
        List<String> textFields = kind.equals("components") ? List.of("name","purpose","platform")
            : List.of("name","sourceSystem","targetSystem","responsibleParty","comment");
        for (String field : textFields) {
            int limit = field.equals("purpose") || field.equals("comment") ? 1000 : 255;
            for (String value : List.of("   ","x".repeat(limit+1),"https://private.example","<script>bad</script>","password=hidden")) {
                ObjectNode bad = valid.deepCopy(); bad.put(field,value);
                JsonNode error = call(post(path("p",kind)),bad.toString(),400);
                assertThat(error.toString()).doesNotContain("hidden","private.example","<script>","java.","stackTrace");
            }
        }
        if (kind.equals("components")) {
            for (String value : List.of("-1","1.5","2147483648","\"0\"","true"))
                call(post(path("p",kind)), valid.toString().replace("\"position\":0","\"position\":"+value),400);
            for (String value : List.of("\"false\"","0")) call(post(path("p",kind)),valid.toString().replace("\"externalComponent\":false","\"externalComponent\":"+value),400);
        } else {
            for (String array : List.of("[]","null","[\"PUBLIC\",\"PUBLIC\"]","[\"public\"]","[null]","[\"UNKNOWN\"]","[1]","[\"PUBLIC\",\"INTERNAL\",\"CONFIDENTIAL\",\"PERSONAL\",\"PAYMENT\",\"COMMERCIAL_SECRET\",\"PUBLIC\"]"))
                call(post(path("p",kind)),valid.toString().replace("[\"PUBLIC\",\"INTERNAL\"]",array),400);
        }
        for (String field : List.of("id","projectId","passport","documentVersion","version","createdAt","updatedAt","expectedVersion","assessment")) {
            ObjectNode bad = valid.deepCopy(); bad.putNull(field); call(post(path("p",kind)),bad.toString(),400);
        }
        for (String raw : List.of("null","[]","{}","{", valid.toString()+" {}",valid.toString().replace("\"name\":\"Name\"","\"name\":\"Name\",\"name\":\"Other\"")," ".repeat(32769)))
            call(post(path("p",kind)),raw,400);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode created = call(post(path("p",kind)),valid.toString(),201); String id = created.path(kind).get(0).path("id").asText(); before = snapshot();
        for (String raw : List.of("{\"expectedVersion\":0}","{\"expectedVersion\":0,\"expectedVersion\":0,\"name\":\"New\"}","{\"expectedVersion\":0,\"id\":\"wrong\"}"))
            call(patch(path("p",kind)+"/"+id),raw,400);
        for (String field : fieldNames(valid)) if (!field.equals("expectedPassportVersion") && !nullable.contains(field))
            call(patch(path("p",kind)+"/"+id),json.createObjectNode().put("expectedVersion",0).putNull(field).toString(),400);
        if (kind.equals("integrations")) call(patch(path("p",kind)+"/"+id),"{\"expectedVersion\":0,\"dataClasses\":[]}",400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void boundaryLengthsNfcMultilineAndClosedCatalogsAreAccepted(String kind) throws Exception {
        ObjectNode body = createBody(kind,0); body.put("name"," e\u0301 ");
        if (kind.equals("components")) {
            body.put("purpose"," First\r\nSecond "); body.put("platform","x".repeat(255)); body.put("position",Integer.MAX_VALUE);
        } else {
            body.put("sourceSystem","s".repeat(255)); body.put("targetSystem","t".repeat(255)); body.put("responsibleParty","r".repeat(255)); body.put("comment"," First\r\nSecond ");
            body.putArray("dataClasses").add("PUBLIC").add("INTERNAL").add("CONFIDENTIAL").add("PERSONAL").add("PAYMENT").add("COMMERCIAL_SECRET");
        }
        JsonNode created = call(post(path("p",kind)),body.toString(),201); JsonNode row = created.path(kind).get(0); String id = row.path("id").asText();
        assertThat(row.path("name").asText()).isEqualTo("é");
        String multiline = kind.equals("components") ? "purpose" : "comment";
        assertThat(row.path(multiline).asText()).isEqualTo("First\nSecond");
        JsonNode patched = call(patch(path("p",kind)+"/"+id),json.createObjectNode().put("expectedVersion",0).put(multiline,"x".repeat(1000)).toString(),200);
        assertThat(row(patched,kind,id).path(multiline).asText()).hasSize(1000);
    }

    @Test
    void integrationClassReplacementIsAtomicAndDoesNotTouchOtherIntegrationOrPassportVersion() throws Exception {
        JsonNode first = call(post(path("p","integrations")),createBody("integrations",0).toString(),201);
        String firstId = first.path("integrations").get(0).path("id").asText();
        JsonNode both = call(post(path("p","integrations")),createBody("integrations",1).put("name","Second").toString(),201);
        JsonNode other = both.path("integrations").findValues("id").stream().map(JsonNode::asText).filter(id -> !id.equals(firstId)).map(id -> row(both,"integrations",id)).findFirst().orElseThrow();
        JsonNode result = call(patch(path("p","integrations")+"/"+firstId),"{\"expectedVersion\":0,\"dataClasses\":[\"PERSONAL\"],\"responsibleParty\":null}",200);
        assertThat(row(result,"integrations",firstId).path("dataClasses").toString()).isEqualTo("[\"PERSONAL\"]");
        assertThat(row(result,"integrations",other.path("id").asText())).isEqualTo(other);
        assertThat(result.at("/passport/version").asLong()).isEqualTo(2);
        assertThat(row(result,"integrations",firstId).path("version").asLong()).isEqualTo(1);
        assertThat(getCard()).isEqualTo(result);
    }

    @Test
    void failureAfterClassReplacementFlushRollsBackRowClassesAndPassportVersion() throws Exception {
        JsonNode created = call(post(path("p","integrations")),createBody("integrations",0).toString(),201);
        String id = created.path("integrations").get(0).path("id").asText(); var before = snapshot();
        AtomicReference<RuntimeException> failure = new AtomicReference<>(new org.springframework.dao.DataAccessResourceFailureException("SQL secret java.sql at C:\\private"));
        doAnswer(call -> {
            call.callRealMethod(); em.flush();
            throw failure.get();
        }).when(classWriter).replace(any(),any());
        JsonNode error = call(patch(path("p","integrations")+"/"+id),"{\"expectedVersion\":0,\"name\":\"Changed\",\"dataClasses\":[\"PERSONAL\"]}",500);
        assertThat(error.path("code").asText()).isEqualTo("DATABASE_ERROR"); assertThat(error.toString()).doesNotContain("SQL","secret","java.sql","C:");
        assertThat(snapshot()).isEqualTo(before);
        failure.set(new jakarta.persistence.PersistenceException("SQL secret internal.Class at C:\\private"));
        JsonNode creationError = call(post(path("p","integrations")),createBody("integrations",1).toString(),500);
        assertThat(creationError.path("code").asText()).isEqualTo("DATABASE_ERROR");
        assertThat(creationError.toString()).doesNotContain("SQL","secret","internal.Class","C:");
        assertThat(snapshot()).isEqualTo(before); noOrphans();
    }

    @Test
    void integrationWritesKeepAgreedAssessmentAndAcceptedRiskSemantics() throws Exception {
        jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,acceptance_justification,version) "
            + "values ('risk','p','Risk','DATA','LOW','CRITICAL','ACCEPTED','Business acceptance',0)");
        var beforeRisk = snapshot().get("project_security_risks");
        JsonNode blocked = call(post(path("p","integrations")),createBody("integrations",0).put("readiness","BLOCKED").toString(),201);
        String id = blocked.path("integrations").get(0).path("id").asText();
        assertThat(blocked.at("/assessment/status").asText()).isEqualTo("BLOCKED");
        assertThat(blocked.at("/assessment/reasons").findValuesAsText("code")).contains("INTEGRATION_BLOCKED","ACCEPTED_HIGH_RISK","CHECKS_INCOMPLETE");
        assertThat(blocked.at("/assessment/acceptedHighRisks/0/acceptanceVerification").asText()).isEqualTo("UNVERIFIED");
        assertThat(blocked.at("/assessment/acceptedHighRisks/0/justification").asText()).isEqualTo("Business acceptance");
        JsonNode low = call(patch(path("p","integrations")+"/"+id),"{\"expectedVersion\":0,\"criticality\":\"LOW\"}",200);
        assertThat(low.at("/assessment/status").asText()).isEqualTo("ATTENTION_REQUIRED");
        assertThat(low.at("/assessment/reasons").findValuesAsText("code")).doesNotContain("INTEGRATION_BLOCKED").contains("ACCEPTED_HIGH_RISK");
        assertThat(low.at("/assessment/officialGoLiveApproval").asBoolean()).isFalse();
        assertThat(snapshot().get("project_security_risks")).isEqualTo(beforeRisk);
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void concurrentCreationWithSamePassportVersionHasOne201One409(String kind) throws Exception { creationRace(kind,kind); }
    @Test void differentChildKindsShareTheSameCreationVersionAndProjectLock() throws Exception { creationRace("components","integrations"); }
    private void creationRace(String firstKind, String secondKind) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode result = unchecked(post(path("p",firstKind)),createBody(firstKind,0).toString(),201);
                written.countDown(); await(release); return result;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(post(path("p",secondKind)),createBody(secondKind,0).toString(),409));
                blocked("projects",second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertVersion(second.get(5,TimeUnit.SECONDS),"PASSPORT",1);
                assertThat(getCard().path("components").size()+getCard().path("integrations").size()).isEqualTo(1); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void concurrentPatchSameRecordWaitsAndReturnsActualRecordVersion(String kind) throws Exception {
        JsonNode created = call(post(path("p",kind)),createBody(kind,0).toString(),201); String id = created.path(kind).get(0).path("id").asText();
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode result = unchecked(patch(path("p",kind)+"/"+id),"{\"expectedVersion\":0,\"name\":\"Winner\"}",200);
                written.countDown(); await(release); return result;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(patch(path("p",kind)+"/"+id),"{\"expectedVersion\":0,\"name\":\"Loser\"}",409));
                blocked("project_architecture_"+kind,second); release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertVersion(second.get(5,TimeUnit.SECONDS),resource(kind),1);
                assertThat(row(getCard(),kind,id).path("name").asText()).isEqualTo("Winner");
                assertThat(getCard().at("/passport/version").asLong()).isEqualTo(1); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void differentRecordsCanFinishPatchWhileFirstRecordStillHoldsLock(String kind) throws Exception {
        JsonNode firstCreated = call(post(path("p",kind)),createBody(kind,0).toString(),201); String firstId = firstCreated.path(kind).get(0).path("id").asText();
        JsonNode both = call(post(path("p",kind)),createBody(kind,1).put("name","Second").toString(),201);
        String secondId = both.path(kind).findValuesAsText("id").stream().filter(id -> !id.equals(firstId)).findFirst().orElseThrow();
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode result = unchecked(patch(path("p",kind)+"/"+firstId),"{\"expectedVersion\":0,\"name\":\"First changed\"}",200);
                written.countDown(); await(release); return result;
            }));
            try {
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> unchecked(patch(path("p",kind)+"/"+secondId),"{\"expectedVersion\":0,\"name\":\"Second changed\"}",200));
                JsonNode own = second.get(3,TimeUnit.SECONDS); assertThat(first.isDone()).isFalse();
                assertThat(row(own,kind,secondId).path("version").asLong()).isEqualTo(1);
                assertThat(own.at("/passport/version").asLong()).isEqualTo(2);
                release.countDown(); first.get(5,TimeUnit.SECONDS);
                assertThat(row(getCard(),kind,firstId).path("version").asLong()).isEqualTo(1);
                assertThat(row(getCard(),kind,secondId).path("version").asLong()).isEqualTo(1);
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void creationAndProjectDeleteUseTheSameLockInBothOrders(String kind) throws Exception {
        deletionRace(kind,false); // Create first, DELETE waits then receives FK conflict.
        // Reinitialize disposable state only; this is not a runtime deletion operation.
        resetState(); deletionRace(kind,true); // DELETE first, conflicts on existing passport, then POST succeeds.
    }
    private void deletionRace(String kind, boolean deleteFirst) throws Exception {
        CountDownLatch acquired = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Thread> deletingThread = new AtomicReference<>();
        if (deleteFirst) doAnswer(call -> {
            Object result = call.callRealMethod();
            if (Thread.currentThread() == deletingThread.get()) { acquired.countDown(); await(release); }
            return result;
        }).when(projectLock).require("p");
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<JsonNode> first;
            if (deleteFirst) first = executor.submit(() -> { deletingThread.set(Thread.currentThread()); return unchecked(delete("/api/projects/p"),"",409); });
            else first = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                JsonNode result = unchecked(post(path("p",kind)),createBody(kind,0).toString(),201);
                acquired.countDown(); await(release); return result;
            }));
            try {
                assertThat(acquired.await(5,TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> deleteFirst ? unchecked(post(path("p",kind)),createBody(kind,0).toString(),201)
                    : unchecked(delete("/api/projects/p"),"",409));
                blocked("projects",second); release.countDown();
                JsonNode firstResult = first.get(5,TimeUnit.SECONDS), secondResult = second.get(5,TimeUnit.SECONDS);
                assertThat((deleteFirst ? firstResult : secondResult).path("code").asText()).isEqualTo("PROJECT_HAS_DEPENDENCIES");
                assertThat(jdbc.queryForObject("select count(*) from projects where id='p'",Long.class)).isEqualTo(1);
                assertThat(getCard().path(kind).size()).isEqualTo(1); noOrphans();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"components", "integrations"})
    void responseIsAssembledInsideWriteTransactionWithItsOwnUncommittedChild(String kind) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Card own = new TransactionTemplate(manager).execute(status -> {
                Card result = kind.equals("components") ? mutations.createComponent("p",((ObjectNode)createBody(kind,0)).put("expectedPassportInstanceId",ArchitectureTestTokens.token(jdbc,"p"))) : mutations.createIntegration("p",((ObjectNode)createBody(kind,0)).put("expectedPassportInstanceId",ArchitectureTestTokens.token(jdbc,"p")));
                assertThat(result.passport().version()).isEqualTo(1);
                try {
                    JsonNode external = executor.submit(() -> getCard()).get(5,TimeUnit.SECONDS);
                    assertThat(external.path(kind).size()).isZero(); assertThat(external.at("/passport/version").asLong()).isZero();
                } catch (Exception error) { throw new AssertionError(error); }
                return result;
            });
            assertThat(getCard()).isEqualTo(json.readTree(json.writeValueAsBytes(own)));
        }
    }

    @Test
    void repeatedGetsPreserveAllRowsDatesVersionsAndOldDomainData() throws Exception {
        jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) values ('card','p','backlog',0,'Task','Description','Owner','')");
        jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,position) values ('milestone','p','Gate','2026-10-01',false,0)");
        jdbc.update("insert into release_trains(id,name,status,cadence,quarter,planned_release_date,code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) values ('train','Train','planned','monthly','Q1','','','','',0,0,0,0,'Scope','Risk','Decision')");
        var old = snapshot();
        JsonNode component = call(post(path("p","components")),createBody("components",0).toString(),201);
        JsonNode integration = call(post(path("p","integrations")),createBody("integrations",1).toString(),201);
        call(patch(path("p","components")+"/"+component.path("components").get(0).path("id").asText()),"{\"expectedVersion\":0,\"name\":\"Changed\"}",200);
        call(patch(path("p","integrations")+"/"+integration.path("integrations").get(0).path("id").asText()),"{\"expectedVersion\":0,\"dataClasses\":[\"PERSONAL\"]}",200);
        var before = snapshot();
        for (String table : List.of("projects","board_cards","project_milestones","release_trains")) assertThat(before.get(table)).isEqualTo(old.get(table));
        for (int repeat = 0; repeat < 3; repeat++) for (String route : List.of("/api/projects/p/architecture","/api/architecture/portfolio",
            "/api/projects/p/architecture/deletion-impact","/api/projects/p/deletion-impact")) call(get(route),null,200);
        assertThat(snapshot()).isEqualTo(before);
    }

    private ObjectNode createBody(String kind, long version) {
        ObjectNode body = json.createObjectNode().put("expectedPassportVersion",version).put("name","Name").put("criticality","HIGH");
        if (kind.equals("components")) body.put("componentType","BACKEND").put("purpose","Purpose").put("platform","JVM").put("externalComponent",false).put("position",0);
        else {
            body.put("sourceSystem","Source").put("targetSystem","Target").put("direction","OUTBOUND").put("interactionType","API")
                .put("authCategory","OAUTH2").put("readiness","READY").put("responsibleParty","Owner").put("comment","Comment");
            body.putArray("dataClasses").add("PUBLIC").add("INTERNAL");
        }
        return body;
    }
    private static List<String> fieldNames(JsonNode body) { var result = new ArrayList<String>(); body.fieldNames().forEachRemaining(result::add); return result; }
    private static String resource(String kind) { return kind.equals("components") ? "COMPONENT" : "INTEGRATION"; }
    private static String path(String project, String kind) { return "/api/projects/"+project+"/architecture/"+kind; }
    private static JsonNode row(JsonNode card, String kind, String id) { for (JsonNode row : card.path(kind)) if (row.path("id").asText().equals(id)) return row; throw new AssertionError("Missing row"); }
    private static void assertVersion(JsonNode error, String resource, long expected) {
        assertThat(error.path("code").asText()).isEqualTo(resource+"_VERSION_CONFLICT");
        assertThat(error.path("versionResource").asText()).isEqualTo(resource); assertThat(error.path("currentVersion").asLong()).isEqualTo(expected);
    }
    private JsonNode call(MockHttpServletRequestBuilder request, String body, int expected) throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        ArchitectureTestTokens.supply(request,jdbc,json);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }
    private JsonNode unchecked(MockHttpServletRequestBuilder request, String body, int expected) {
        try { return call(request,body,expected); } catch (Exception error) { throw new AssertionError(error); }
    }
    private JsonNode getCard() throws Exception { return call(get("/api/projects/p/architecture"),null,200); }
    private Map<String,List<Map<String,Object>>> snapshot() {
        var result = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table : TABLES) result.put(table,jdbc.queryForList("select * from "+table).stream().sorted(java.util.Comparator.comparing(Object::toString)).toList());
        return result;
    }
    private void noOrphans() {
        for (String table : List.of("project_architecture_components","project_architecture_integrations"))
            assertThat(jdbc.queryForObject("select count(*) from "+table+" c left join project_architecture_passports a on a.project_id=c.project_id where a.project_id is null",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from project_architecture_passports a left join projects p on p.id=a.project_id where p.id is null",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from project_integration_data_classes d left join project_architecture_integrations i on i.id=d.integration_id where i.id is null",Long.class)).isZero();
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private void blocked(String table, Future<?> second) {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
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
}
