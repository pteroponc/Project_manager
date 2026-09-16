package project_manager.safety;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import project_manager.ProjectManagerApplication;
import project_manager.domain.*;
import project_manager.repository.*;
import project_manager.service.*;
import project_manager.web.dto.BoardCardRequest;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class PersistenceSafetyIntegrationTest {
    public static class Writes implements StatementInspector {
        static final AtomicInteger count = new AtomicInteger();
        public String inspect(String sql) {
            if (sql.stripLeading().matches("(?is)(insert|update|delete|merge|alter|create|drop|truncate)\\b.*")) count.incrementAndGet();
            return sql;
        }
    }

    @Test
    void readsAndTwoRestartsPreserveEveryStoredField() throws Exception {
        String url = "jdbc:h2:mem:pmtest_restart_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Map<String, List<Map<String, Object>>> expected;
        try (var context = open(url, "create")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select count(*) from projects", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from release_trains", Integer.class)).isZero();
            var project = strings(new ProjectEntity());
            project.setId("p"); project.setName("Digital Commerce Platform");
            project.setDeliveryModel("kanban"); project.setStatus("active"); project.setHealth("green");
            project.setDeadline("2031-01-01");
            context.getBean(ProjectRepository.class).save(project);
            var train = strings(new ReleaseTrainEntity());
            train.setId("t"); train.setName("RT-2026-Q3 Platform"); train.setStatus("planning");
            context.getBean(ReleaseTrainRepository.class).save(train);
            var card = strings(new BoardCardEntity());
            card.setId("p-scope"); card.setProjectId("p"); card.setColumnKey("legacy-column");
            card.setPosition(42); card.setPriority("high"); card.setDueDate("2031-01-02");
            context.getBean(BoardCardRepository.class).save(card);
            for (int position : List.of(-7, 42, Integer.MAX_VALUE)) {
                var legacy = strings(new BoardCardEntity());
                legacy.setId("legacy-" + position); legacy.setProjectId("p");
                legacy.setColumnKey("legacy-column"); legacy.setPosition(position);
                legacy.setPriority("low");
                context.getBean(BoardCardRepository.class).save(legacy);
            }

            // Explicit mutations still work; deleted cards must not return on GET.
            var boards = context.getBean(BoardService.class);
            var created = boards.createCard("p", new BoardCardRequest("Task", "Text", "Owner", "2030-01-01", "backlog", "low", "tag", 1, false));
            boards.updateCard("p", created.id(), new BoardCardRequest("Edited", "Text", "Owner", "2030-01-02", "done", "high", "tag", 2, true));
            boards.deleteCard("p", created.id());
            var deletedTemplate = strings(new BoardCardEntity());
            deletedTemplate.setId("p-risk"); deletedTemplate.setProjectId("p"); deletedTemplate.setColumnKey("ready");
            context.getBean(BoardCardRepository.class).save(deletedTemplate);
            boards.deleteCard("p", "p-risk");
            expected = snapshot(jdbc);
            verifyReads(context, expected);
        }
        for (int restart = 0; restart < 2; restart++) {
            Writes.count.set(0);
            try (var context = open(url, "validate")) {
                assertThat(Writes.count).hasValue(0);
                verifyReads(context, expected);
            }
        }
    }

    @Test
    void unsafeSpringBootLaunchIsRejectedBeforeContextCreation() {
        assertThatThrownBy(() -> open("jdbc:h2:file:./target/forbidden-test-target;IFEXISTS=TRUE", "validate"))
            .hasStackTraceContaining("approved in-memory H2 URL");
    }

    @Test
    void validationRejectsIncompatibleSyntheticSchemaWithoutRepair() throws Exception {
        String url = "jdbc:h2:mem:pmtest_mismatch_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var context = open(url, "create")) {
            context.getBean(JdbcTemplate.class).execute("alter table projects drop column owner");
        }
        Writes.count.set(0);
        assertThatThrownBy(() -> open(url, "validate")).hasStackTraceContaining("missing column [owner]");
        assertThat(Writes.count).hasValue(0);
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
             var columns = connection.getMetaData().getColumns(null, null, "PROJECTS", "OWNER")) {
            assertThat(columns.next()).isFalse();
        }
    }

    @Test
    void validationOnNewEmptyMemoryDatabaseFailsWithoutCreatingTables() throws Exception {
        String url = "jdbc:h2:mem:pmtest_empty_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        assertThatThrownBy(() -> open(url, "validate")).hasStackTraceContaining("missing table");
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
             var tables = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) {
            assertThat(tables.next()).isFalse();
        }
    }

    @Test
    void explicitlyInitializedNewMemorySchemaStartsWithValidateAndNoDemoRecords() {
        String url = "jdbc:h2:mem:pmtest_new_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        // Only a newly generated in-memory database; never an existing or file URL.
        try (var initialized = open(url, "create")) {
            assertThat(snapshot(initialized.getBean(JdbcTemplate.class)).values()).allSatisfy(rows -> assertThat(rows).isEmpty());
        }
        try (var validated = open(url, "validate")) {
            assertThat(snapshot(validated.getBean(JdbcTemplate.class)).values()).allSatisfy(rows -> assertThat(rows).isEmpty());
        }
    }

    private static ConfigurableApplicationContext open(String url, String ddl) {
        return open(url, ddl, "default");
    }

    @Test
    void demoRestartPreservesEditedRecordsAndDoesNotRefillDeletedTask() {
        String url = "jdbc:h2:mem:pmtest_demo_restart_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Map<String, List<Map<String, Object>>> expected;
        try (var context = open(url, "create", "demo")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select count(*) from projects", Integer.class)).isEqualTo(3);
            jdbc.update("update projects set owner='Edited demo owner'");
            jdbc.update("delete from board_cards where id=(select min(id) from board_cards)");
            expected = snapshot(jdbc);
        }
        try (var context = open(url, "validate", "demo")) {
            assertThat(snapshot(context.getBean(JdbcTemplate.class))).isEqualTo(expected);
        }
    }

    @Test
    void smokeProfileDoesNotSeedDemoData() {
        String url = "jdbc:h2:mem:pmtest_smoke_" + UUID.randomUUID().toString().replace("-", "");
        try (var context = open(url, "create-drop", "smoke")) {
            assertThat(snapshot(context.getBean(JdbcTemplate.class)).values()).allSatisfy(rows -> assertThat(rows).isEmpty());
            assertThat(context.getBeansOfType(DemoDataInitializer.class)).isEmpty();
        }
    }

    private static ConfigurableApplicationContext open(String url, String ddl, String profile) {
        return new SpringApplicationBuilder(ProjectManagerApplication.class).web(WebApplicationType.NONE).run(
            "--spring.datasource.url=" + url, "--spring.jpa.hibernate.ddl-auto=" + ddl,
            "--spring.profiles.active=" + profile, "--logging.level.root=ERROR",
            "--spring.jpa.properties.hibernate.session_factory.statement_inspector=" + Writes.class.getName());
    }

    private static void verifyReads(ConfigurableApplicationContext context, Map<String, List<Map<String, Object>>> expected) {
        Writes.count.set(0);
        var projects = context.getBean(ProjectService.class);
        projects.getProjects("all", "all"); projects.getProject("p");
        context.getBean(PortfolioService.class).getSnapshot("all", "all");
        context.getBean(ReleaseTrainService.class).snapshot("all", "all");
        for (String model : List.of("kanban", "scrum", "waterfall", "kanban")) {
            var board = context.getBean(BoardService.class).getBoard("p", model);
            assertThat(board.columns()).allSatisfy(column -> assertThat(column.cards()).isEmpty());
            assertThat(board.unassignedColumns()).hasSize(1);
            assertThat(board.unassignedColumns().getFirst().key()).isEqualTo("legacy-column");
            assertThat(board.unassignedColumns().getFirst().cards()).extracting(BoardCard::id)
                .containsExactlyInAnyOrder("p-scope", "legacy--7", "legacy-42", "legacy-2147483647");
            assertThat(board.storedPositions()).containsEntry("p-scope", 42)
                .containsEntry("legacy--7", -7).containsEntry("legacy-42", 42)
                .containsEntry("legacy-2147483647", Integer.MAX_VALUE);
        }
        assertThat(Writes.count).hasValue(0);
        assertThat(snapshot(context.getBean(JdbcTemplate.class))).isEqualTo(expected);
    }

    private static Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("projects", "release_trains", "board_cards")) {
            snapshot.put(table, jdbc.queryForList("select * from " + table + " order by id"));
        }
        return snapshot;
    }

    private static <T> T strings(T entity) throws Exception {
        for (var field : entity.getClass().getDeclaredFields()) {
            if (field.getType() == String.class) { field.setAccessible(true); field.set(entity, "User value"); }
        }
        return entity;
    }
}
