package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import project_manager.ProjectManagerApplication;
import project_manager.service.BoardService;
import project_manager.service.ProjectDeletionService;
import project_manager.web.ProjectConflictException;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.BoardCardRequest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectCardSerializationIntegrationTest {
    @Test
    void cardLockFirstMakesDeleteReturnConflictAndPreservesBothRows() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            BoardService boards = context.getBean(BoardService.class);
            ProjectDeletionService deletion = context.getBean(ProjectDeletionService.class);
            TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            CountDownLatch cardWritten = new CountDownLatch(1);
            CountDownLatch releaseCard = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var create = executor.submit(() -> tx.executeWithoutResult(status -> {
                    boards.createCard("p1", request("First"));
                    cardWritten.countDown();
                    await(releaseCard);
                }));
                assertThat(cardWritten.await(5, TimeUnit.SECONDS)).isTrue();
                var delete = executor.submit(() -> {
                    try { deletion.delete("p1"); return (Throwable) null; }
                    catch (Throwable error) { return error; }
                });
                awaitBlockedProjectLock(jdbc);
                assertThat(delete.isDone()).isFalse();
                releaseCard.countDown();
                create.get(5, TimeUnit.SECONDS);
                assertThat(delete.get(5, TimeUnit.SECONDS)).isInstanceOf(ProjectConflictException.class);
                assertThat(jdbc.queryForObject("select count(*) from projects where id='p1'", Integer.class))
                    .isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from board_cards where project_id='p1'",
                    Integer.class)).isEqualTo(1);
                assertNoOrphans(jdbc);
            } finally { releaseCard.countDown(); executor.shutdownNow(); }
        }
    }

    @Test
    void deleteLockFirstMakesCreateReturnNotFoundWithoutOrphan() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            BoardService boards = context.getBean(BoardService.class);
            ProjectDeletionService deletion = context.getBean(ProjectDeletionService.class);
            TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            CountDownLatch projectDeleted = new CountDownLatch(1);
            CountDownLatch releaseDelete = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var delete = executor.submit(() -> tx.executeWithoutResult(status -> {
                    deletion.delete("p1");
                    projectDeleted.countDown();
                    await(releaseDelete);
                }));
                assertThat(projectDeleted.await(5, TimeUnit.SECONDS)).isTrue();
                var create = executor.submit(() -> {
                    try { boards.createCard("p1", request("Too late")); return (Throwable) null; }
                    catch (Throwable error) { return error; }
                });
                awaitBlockedProjectLock(jdbc);
                assertThat(create.isDone()).isFalse();
                releaseDelete.countDown();
                delete.get(5, TimeUnit.SECONDS);
                assertThat(create.get(5, TimeUnit.SECONDS)).isInstanceOf(ProjectMutationException.class)
                    .satisfies(error -> assertThat(((ProjectMutationException) error).getCode())
                        .isEqualTo("PROJECT_NOT_FOUND"));
                assertThat(jdbc.queryForObject("select count(*) from projects where id='p1'", Integer.class))
                    .isZero();
                assertThat(jdbc.queryForObject("select count(*) from board_cards where project_id='p1'",
                    Integer.class)).isZero();
                assertNoOrphans(jdbc);
            } finally { releaseDelete.countDown(); executor.shutdownNow(); }
        }
    }

    @Test
    void transferLocksBothProjectsInStableOrderAndFailedTransferPreservesCard() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            BoardService boards = context.getBean(BoardService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String cardId = boards.createCard("p1", request("Move me")).id();
            boards.transferCard("p1", "p2", cardId, request("Moved"));
            assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, cardId))
                .isEqualTo("p2");
            assertThatThrownBy(() -> boards.transferCard("p2", "missing", cardId, request("Bad")))
                .isInstanceOf(ProjectMutationException.class);
            assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, cardId))
                .isEqualTo("p2");
            String oppositeId = boards.createCard("p1", request("Other")).id();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var left = executor.submit(() -> {
                    ready.countDown();
                    await(go);
                    boards.transferCard("p2", "p1", cardId, request("Left"));
                });
                var right = executor.submit(() -> {
                    ready.countDown();
                    await(go);
                    boards.transferCard("p1", "p2", oppositeId, request("Right"));
                });
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                left.get(5, TimeUnit.SECONDS);
                right.get(5, TimeUnit.SECONDS);
            } finally { go.countDown(); executor.shutdownNow(); }
            assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, cardId))
                .isEqualTo("p1");
            assertThat(jdbc.queryForObject("select project_id from board_cards where id=?", String.class, oppositeId))
                .isEqualTo("p2");
            assertNoOrphans(jdbc);
        }
    }

    private static ConfigurableApplicationContext context() throws Exception {
        String url = "jdbc:h2:mem:pmtest_serial_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("pm002a-old-schema.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/manual/V002__project_model_and_milestones.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/manual/V003__board_cards_project_fk.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/manual/V004__architecture_security.sql"));
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            for (String id : new String[]{"p1", "p2"}) {
                jdbc.update("insert into projects(id,name,owner,status,health,delivery_model,budget,progress,quarter,"
                    + "deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,version) "
                    + "values (?,?, 'Owner','active','green','kanban',0,0,'Q1','2027-01-01',"
                    + "'Milestone','Risk','Dependency','KPI','Target','Summary',0)", id, id);
            }
        }
        return new SpringApplicationBuilder(ProjectManagerApplication.class)
            .web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.profiles.active=default",
                "--logging.level.root=ERROR");
    }

    private static BoardCardRequest request(String title) {
        return new BoardCardRequest(title, "Description", "Owner", "2027-01-01", "backlog",
            "medium", "tag", 1, false);
    }

    private static void assertNoOrphans(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select count(*) from board_cards b left join projects p "
            + "on p.id=b.project_id where p.id is null", Integer.class)).isZero();
    }

    private static void awaitBlockedProjectLock(JdbcTemplate jdbc) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<Map<String, Object>> sessions = List.of();
        while (System.nanoTime() < deadline) {
            sessions = jdbc.queryForList("select session_state, blocker_id, executing_statement "
                + "from information_schema.sessions");
            if (sessions.stream().anyMatch(session ->
                "BLOCKED".equals(session.get("SESSION_STATE"))
                    && session.get("BLOCKER_ID") != null
                    && String.valueOf(session.get("EXECUTING_STATEMENT")).toLowerCase().contains("projects"))) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
        }
        throw new AssertionError("Second request did not reach the blocked project lock: " + sessions);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for barrier");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Barrier interrupted", exception);
        }
    }
}
