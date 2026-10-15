package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import project_manager.ProjectManagerApplication;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardCardsProjectFkMigrationIntegrationTest {
    private static final Path V002 = Path.of("db/manual/V002__project_model_and_milestones.sql");
    private static final Path V003 = Path.of("db/manual/V003__board_cards_project_fk.sql");

    @Test
    void migrationPreservesEveryOldRowAndEnforcesRelationship() throws Exception {
        for (boolean nullableSource : List.of(false, true)) {
            String url = memoryUrl();
            try (Connection connection = setup(url)) {
                JdbcTemplate jdbc = jdbc(connection);
                if (nullableSource) jdbc.execute("alter table board_cards alter column project_id drop not null");
                Map<String, List<Map<String, Object>>> before = rows(jdbc);

                migrate(connection);

                assertThat(rows(jdbc)).isEqualTo(before);
                assertFkMetadata(jdbc);
                assertThat(nullable(jdbc)).isEqualTo("NO");
                if (!nullableSource) {
                    assertThat(jdbc.queryForObject("select @PM003B1A_NOOP", Integer.class)).isEqualTo(1);
                }
                assertThatThrownBy(() -> insertCard(jdbc, "orphan-after", "missing"))
                    .isInstanceOf(Exception.class).hasMessageContaining("FK_BOARD_CARDS_PROJECT");
                assertThatThrownBy(() -> insertCard(jdbc, "null-after", null))
                    .isInstanceOf(Exception.class);
                assertThatThrownBy(() -> jdbc.update("delete from projects where id='project-1'"))
                    .isInstanceOf(Exception.class).hasMessageContaining("FK_BOARD_CARDS_PROJECT");
                assertThat(rows(jdbc)).isEqualTo(before);
            }
        }
    }

    @Test
    void rejectsNullAndOrphanBeforeFirstDdlWithoutTouchingRows() throws Exception {
        for (String invalid : List.of("null", "orphan")) {
            try (Connection connection = setup(memoryUrl())) {
                JdbcTemplate jdbc = jdbc(connection);
                if (invalid.equals("null")) {
                    jdbc.execute("alter table board_cards alter column project_id drop not null");
                    insertCard(jdbc, "invalid", null);
                } else {
                    insertCard(jdbc, "invalid", "absent-project");
                }
                assertRejectedWithoutDdl(connection);
            }
        }
    }

    @Test
    void rejectsIncompatiblePartialAndRepeatedSchemaBeforeDdl() throws Exception {
        try (Connection connection = setup(memoryUrl())) {
            jdbc(connection).execute("alter table board_cards alter column title varchar(300) not null");
            assertRejectedWithoutDdl(connection);
        }
        try (Connection connection = setup(memoryUrl())) {
            jdbc(connection).execute("create index idx_board_cards_project on board_cards(project_id)");
            assertRejectedWithoutDdl(connection);
        }
        try (Connection connection = setup(memoryUrl())) {
            jdbc(connection).execute("alter table board_cards add constraint fk_board_cards_project "
                + "foreign key(project_id) references projects(id) on delete no action");
            assertRejectedWithoutDdl(connection);
        }
        try (Connection connection = setup(memoryUrl())) {
            migrate(connection);
            assertRejectedWithoutDdl(connection);
        }
    }

    @Test
    void committedDeleteBeforeInsertIsRejectedWithoutOrphan() throws Exception {
        String url = memoryUrl();
        try (Connection setup = setup(url)) { migrate(setup); }
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            JdbcTemplate jdbc = jdbc(connection);
            jdbc.update("insert into projects(id,name,owner,status,health,delivery_model,budget,progress,quarter,"
                + "deadline,milestone,risk,dependency,kpi_name,kpi_target,summary,version) "
                + "values ('project-4','Fourth','Owner','planned','green',null,null,null,'Q2','2027-06-01',"
                + "'Milestone','Risk','Dependency','KPI','Target','Summary',0)");
            jdbc.update("delete from projects where id='project-4'");
            assertThatThrownBy(() -> insertCard(jdbc, "too-late", "project-4"))
                .isInstanceOf(Exception.class).hasMessageContaining("FK_BOARD_CARDS_PROJECT");
            assertThat(jdbc.queryForObject("select count(*) from board_cards where project_id='project-4'",
                Integer.class)).isZero();
        }
    }

    @Test
    void hibernateValidateIsSupplementedByDirectFkMetadataCheck() throws Exception {
        String url = memoryUrl();
        try (Connection setup = setup(url)) {
            migrate(setup);
            ScriptUtils.executeSqlScript(setup,
                new FileSystemResource("db/manual/V004__architecture_security.sql"));
            ScriptUtils.executeSqlScript(setup, new FileSystemResource("db/manual/V005__architecture_passport_instance.sql"));
        }
        try (var context = new SpringApplicationBuilder(ProjectManagerApplication.class)
            .web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.profiles.active=default",
                "--logging.level.root=ERROR")) {
            assertFkMetadata(context.getBean(JdbcTemplate.class));
        }
    }

    private static Connection setup(String url) throws Exception {
        Connection connection = DriverManager.getConnection(url, "sa", "");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("pm002a-old-schema.sql"));
        JdbcTemplate jdbc = jdbc(connection);
        jdbc.update("insert into projects(id,name,owner,status,health,delivery_model,budget,progress,quarter,"
            + "deadline,milestone,risk,dependency,kpi_name,kpi_target,summary) "
            + "values ('project-1','First','Owner','active','green','kanban',0,0,'Q1','2027-01-01',"
            + "'Milestone','Risk','Dependency','KPI','Target','Summary')");
        jdbc.update("insert into projects(id,name,owner,status,health,delivery_model,budget,progress,quarter,"
            + "deadline,milestone,risk,dependency,kpi_name,kpi_target,summary) "
            + "values ('project-2','Second','Owner','planned','yellow',null,2147483647,73,'Q2','2027-06-01',"
            + "'Milestone','Risk','Dependency','KPI','Target','Summary')");
        jdbc.update("insert into projects(id,name,owner,status,health,delivery_model,budget,progress,quarter,"
            + "deadline,milestone,risk,dependency,kpi_name,kpi_target,summary) "
            + "values ('project-3','Third','Owner','planned','green',null,0,0,'Q2','2027-06-01',"
            + "'Milestone','Risk','Dependency','KPI','Target','Summary')");
        insertCard(jdbc, "old-card", "project-1");
        jdbc.update("insert into release_trains(id,name,status,cadence,quarter,planned_release_date,"
            + "code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,"
            + "blocked_items,scope,risk,decision) values ('train','Train','planned','monthly','Q2',"
            + "'2027-06-01','2027-05-20','2027-05-25','2027-06-01',144,89,61,4,'Scope','Risk','Decision')");
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(V002));
        jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,completed_date,"
            + "position) values ('point','project-2','Checkpoint',DATE '2027-01-01',false,null,2)");
        return connection;
    }

    private static void insertCard(JdbcTemplate jdbc, String id, String projectId) {
        jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date,"
                + "priority,labels,estimate,blocked) values (?,?,?,?,?,?,?,?,?,?,?,?)",
            id, projectId, "custom", 47, "Card", "Preserve details", "Card owner", "2027-01-02",
            "urgent", "safety", 13, true);
    }

    private static void migrate(Connection connection) {
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(V003));
    }

    private static void assertRejectedWithoutDdl(Connection connection) {
        JdbcTemplate jdbc = jdbc(connection);
        var before = rows(jdbc);
        String nullable = nullable(jdbc);
        int indexes = jdbc.queryForObject("select count(*) from information_schema.indexes "
            + "where table_schema='PUBLIC' and table_name='BOARD_CARDS'", Integer.class);
        int foreignKeys = jdbc.queryForObject("select count(*) from information_schema.table_constraints "
            + "where table_schema='PUBLIC' and table_name='BOARD_CARDS' and constraint_type='FOREIGN KEY'",
            Integer.class);
        assertThatThrownBy(() -> migrate(connection)).isInstanceOf(ScriptException.class)
            .hasStackTraceContaining("PM003B1A_PRECONDITION_FAILED_EXPECTED_PM002C_SCHEMA_OR_VALID_CARDS");
        assertThat(rows(jdbc)).isEqualTo(before);
        assertThat(nullable(jdbc)).isEqualTo(nullable);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes "
            + "where table_schema='PUBLIC' and table_name='BOARD_CARDS'", Integer.class)).isEqualTo(indexes);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints "
            + "where table_schema='PUBLIC' and table_name='BOARD_CARDS' and constraint_type='FOREIGN KEY'",
            Integer.class)).isEqualTo(foreignKeys);
    }

    private static void assertFkMetadata(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.index_columns "
            + "where table_schema='PUBLIC' and table_name='BOARD_CARDS' "
            + "and index_name='IDX_BOARD_CARDS_PROJECT' and column_name='PROJECT_ID' "
            + "and ordinal_position=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.key_column_usage "
            + "where constraint_schema='PUBLIC' and table_name='BOARD_CARDS' "
            + "and constraint_name='FK_BOARD_CARDS_PROJECT' and column_name='PROJECT_ID' "
            + "and ordinal_position=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select delete_rule from information_schema.referential_constraints "
            + "where constraint_schema='PUBLIC' and constraint_name='FK_BOARD_CARDS_PROJECT'",
            String.class)).isIn("NO ACTION", "RESTRICT");
    }

    private static String nullable(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select is_nullable from information_schema.columns "
            + "where table_schema='PUBLIC' and table_name='BOARD_CARDS' and column_name='PROJECT_ID'",
            String.class);
    }

    private static Map<String, List<Map<String, Object>>> rows(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
        for (String table : List.of("projects", "board_cards", "release_trains", "project_milestones")) {
            snapshot.put(table, jdbc.queryForList("select * from " + table + " order by id"));
        }
        return snapshot;
    }

    private static JdbcTemplate jdbc(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    private static String memoryUrl() {
        return "jdbc:h2:mem:pmtest_v003_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }
}
