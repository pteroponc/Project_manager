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
import project_manager.domain.ProjectMilestoneEntity;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.repository.ProjectRepository;
import project_manager.service.ProjectCardService;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectSchemaMigrationIntegrationTest {
    private static final Path MIGRATION = Path.of("db", "manual", "V002__project_model_and_milestones.sql");

    @Test
    void manualMigrationPreservesPm002aRowsAndValidatesNewModel() throws Exception {
        String url = memoryUrl("migration");
        Map<String, List<Map<String, Object>>> before;
        List<String> sqlSnapshot;

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("pm002a-old-schema.sql"));
            JdbcTemplate jdbc = jdbc(connection);
            insertLegacyData(jdbc);
            before = legacySnapshot(jdbc);
            sqlSnapshot = sqlSnapshot(connection);
            assertThat(sqlSnapshot).anySatisfy(line -> assertThat(line).contains("INSERT INTO \"PUBLIC\".\"PROJECTS\""));
            assertThat(sqlSnapshot).anySatisfy(line -> assertThat(line).contains("INSERT INTO \"PUBLIC\".\"BOARD_CARDS\""));
            assertThat(sqlSnapshot).anySatisfy(line -> assertThat(line).contains("INSERT INTO \"PUBLIC\".\"RELEASE_TRAINS\""));

            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATION));

            assertThat(legacySnapshot(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForList("select id, budget, progress, version from projects order by id"))
                .allSatisfy(row -> assertThat(((Number) row.get("VERSION")).longValue()).isZero());
            assertThat(jdbc.queryForObject("select budget from projects where id='zero'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select progress from projects where id='zero'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select budget from projects where id='large'", Integer.class))
                .isEqualTo(Integer.MAX_VALUE);
            assertThat(nullable(jdbc, "BUDGET")).isEqualTo("YES");
            assertThat(nullable(jdbc, "PROGRESS")).isEqualTo("YES");
            assertThat(nullable(jdbc, "START_DATE")).isEqualTo("YES");
            assertThat(nullable(jdbc, "VERSION")).isEqualTo("NO");
            assertThat(columnType(jdbc, "START_DATE")).isEqualTo("DATE");
            assertThat(columnType(jdbc, "VERSION")).isEqualTo("BIGINT");
            assertThat(jdbc.queryForObject("select column_default from information_schema.columns "
                + "where table_schema='PUBLIC' and table_name='PROJECTS' and column_name='VERSION'", String.class))
                .isEqualTo("0");
            assertThat(jdbc.queryForObject("select count(*) from project_milestones", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints "
                + "where table_schema='PUBLIC' and table_name='PROJECT_MILESTONES' "
                + "and constraint_type='FOREIGN KEY'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select delete_rule from information_schema.referential_constraints "
                + "where constraint_schema='PUBLIC' and constraint_name='FK_PROJECT_MILESTONES_PROJECT'", String.class))
                .isIn("NO ACTION", "RESTRICT");
            assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes "
                + "where table_schema='PUBLIC' and table_name='PROJECT_MILESTONES' "
                + "and index_name='IDX_PROJECT_MILESTONES_PROJECT_ORDER'", Integer.class)).isEqualTo(1);

            var afterFirstMigration = jdbc.queryForList("select * from projects order by id");
            assertThatThrownBy(() -> ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATION)))
                .isInstanceOf(ScriptException.class)
                .hasStackTraceContaining("PM002B1_PRECONDITION_FAILED_EXPECTED_PM002A_SCHEMA");
            assertThat(jdbc.queryForList("select * from projects order by id")).isEqualTo(afterFirstMigration);
        }

        try (var context = new SpringApplicationBuilder(ProjectManagerApplication.class)
            .web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.profiles.active=default",
                "--logging.level.root=ERROR")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var beforeReads = fullSnapshot(jdbc);
            var project = context.getBean(ProjectRepository.class).findById("zero").orElseThrow();
            assertThat(project.getBudget()).isZero();
            assertThat(project.getProgress()).isZero();
            assertThat(project.getVersion()).isZero();
            context.getBean(ProjectCardService.class).getCard("zero");
            ProjectMilestoneRepository milestones = context.getBean(ProjectMilestoneRepository.class);
            ProjectMilestoneEntity milestone = new ProjectMilestoneEntity();
            milestone.setId("migrated-schema-milestone");
            milestone.setProject(project);
            milestone.setName("Migration verification");
            milestone.setPlannedDate(java.time.LocalDate.of(2027, 1, 2));
            milestone.setCompleted(false);
            milestone.setPosition(3);
            milestones.saveAndFlush(milestone);
            var afterWrite = fullSnapshot(jdbc);
            assertThat(milestones.findByProject_IdOrderByPositionAscIdAsc("zero"))
                .extracting(ProjectMilestoneEntity::getId)
                .containsExactly("migrated-schema-milestone");
            assertThat(fullSnapshot(jdbc)).isEqualTo(afterWrite);
            assertThat(beforeReads.get("projects")).isEqualTo(afterWrite.get("projects"));
        }
    }

    @Test
    void incompatiblePm002aSchemaIsRejectedBeforeAnyDdlAndKeepsAllRows() throws Exception {
        String url = memoryUrl("incompatible");
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("pm002a-old-schema.sql"));
            JdbcTemplate jdbc = jdbc(connection);
            insertLegacyData(jdbc);
            jdbc.execute("alter table projects alter column budget bigint not null");
            var before = legacySnapshot(jdbc);

            assertThatThrownBy(() -> ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATION)))
                .isInstanceOf(ScriptException.class)
                .hasStackTraceContaining("PM002B1_PRECONDITION_FAILED_EXPECTED_PM002A_SCHEMA");

            assertThat(columnCount(jdbc, "PROJECTS", "START_DATE")).isZero();
            assertThat(columnCount(jdbc, "PROJECTS", "VERSION")).isZero();
            assertThat(nullable(jdbc, "BUDGET")).isEqualTo("NO");
            assertThat(nullable(jdbc, "PROGRESS")).isEqualTo("NO");
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema='PUBLIC' and table_name='PROJECT_MILESTONES'", Integer.class)).isZero();
            assertThat(legacySnapshot(jdbc)).isEqualTo(before);
        }
    }

    private static String memoryUrl(String suffix) {
        return "jdbc:h2:mem:pmtest_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }

    private static JdbcTemplate jdbc(java.sql.Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    private static void insertLegacyData(JdbcTemplate jdbc) {
        String sql = "insert into projects (id,name,owner,status,health,delivery_model,budget,progress,quarter,"
            + "deadline,milestone,risk,dependency,kpi_name,kpi_target,summary) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbc.update(sql, "zero", "Zero", "Owner", "active", "green", "kanban", 0, 0, "Q3", "2027-01-01",
            "M", "R", "D", "K", "T", "Zero values");
        jdbc.update(sql, "large", "Large", "Owner", "planned", "yellow", "scrum", Integer.MAX_VALUE,
            73, "Q4", "2027-06-01", "M", "R", "D", "K", "T", "Maximum legacy budget");
        jdbc.update("insert into board_cards (id,project_id,column_key,position,title,description,owner,due_date,"
                + "priority,labels,estimate,blocked) values (?,?,?,?,?,?,?,?,?,?,?,?)",
            "card-1", "zero", "custom-column", 47, "Legacy card", "Preserve every field", "Card owner",
            "2027-01-02", "urgent-custom", "legacy,safety", 13, true);
        jdbc.update("insert into release_trains (id,name,status,cadence,quarter,planned_release_date,"
                + "code_freeze_date,qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,"
                + "blocked_items,scope,risk,decision) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            "train-1", "Legacy train", "custom-status", "monthly", "Q4", "2027-06-01", "2027-05-20",
            "2027-05-25", "2027-06-01", 144, 89, 61, 4, "Legacy scope", "Legacy risk", "Legacy decision");
    }

    private static Map<String, List<Map<String, Object>>> legacySnapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> result = new java.util.LinkedHashMap<>();
        result.put("projects", jdbc.queryForList("select id,name,owner,status,health,delivery_model,budget,progress,"
            + "quarter,deadline,milestone,risk,dependency,kpi_name,kpi_target,summary from projects order by id"));
        result.put("board_cards", jdbc.queryForList("select * from board_cards order by id"));
        result.put("release_trains", jdbc.queryForList("select * from release_trains order by id"));
        return result;
    }

    private static List<String> sqlSnapshot(java.sql.Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SCRIPT")) {
            var result = new java.util.ArrayList<String>();
            while (rows.next()) result.add(rows.getString(1));
            return List.copyOf(result);
        }
    }

    private static String nullable(JdbcTemplate jdbc, String column) {
        return jdbc.queryForObject("select is_nullable from information_schema.columns "
            + "where table_schema='PUBLIC' and table_name='PROJECTS' and column_name=?", String.class, column);
    }

    private static String columnType(JdbcTemplate jdbc, String column) {
        return jdbc.queryForObject("select data_type from information_schema.columns "
            + "where table_schema='PUBLIC' and table_name='PROJECTS' and column_name=?", String.class, column);
    }

    private static int columnCount(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("select count(*) from information_schema.columns "
            + "where table_schema='PUBLIC' and table_name=? and column_name=?", Integer.class, table, column);
    }

    private static Map<String, List<Map<String, Object>>> fullSnapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> result = new java.util.LinkedHashMap<>();
        for (String table : List.of("projects", "board_cards", "release_trains", "project_milestones")) {
            result.put(table, jdbc.queryForList("select * from " + table + " order by id"));
        }
        return result;
    }
}
