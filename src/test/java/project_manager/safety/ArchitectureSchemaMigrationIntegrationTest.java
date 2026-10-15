package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import project_manager.ProjectManagerApplication;
import project_manager.domain.*;
import project_manager.repository.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ArchitectureSchemaMigrationIntegrationTest {
    private static final List<String> OLD = List.of("projects", "board_cards", "release_trains", "project_milestones");
    private static final List<String> NEW = List.of("project_architecture_passports", "project_architecture_data_classes",
        "project_architecture_environments", "project_architecture_components", "project_architecture_integrations",
        "project_integration_data_classes", "project_architecture_checks", "project_security_risks");

    @Test
    void migrationChainPreservesEveryLegacyValueAndChecksPhysicalSchema() throws Exception {
        try (Connection connection = setup(memoryUrl(), false)) {
            JdbcTemplate jdbc = jdbc(connection);
            var before = snapshot(jdbc, OLD);
            script(connection, "V003__board_cards_project_fk.sql");
            assertThat(snapshot(jdbc, OLD)).isEqualTo(before);
            script(connection, "V004__architecture_security.sql");
            assertThat(snapshot(jdbc, OLD)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select budget from projects where id='zero'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select progress from projects where id='zero'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select budget from projects where id='max'", Integer.class))
                .isEqualTo(Integer.MAX_VALUE);
            assertThat(jdbc.queryForObject("select version from projects where id='max'", Long.class)).isEqualTo(9L);
            assertThat(jdbc.queryForObject("select count(*) from pm_architecture_schema_migrations "
                + "where migration_version='V004' and applied_at is not null", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList("select tc.table_name, rc.delete_rule from information_schema.table_constraints tc "
                + "join information_schema.referential_constraints rc on rc.constraint_name=tc.constraint_name "
                + "and rc.constraint_schema=tc.constraint_schema where tc.table_schema='PUBLIC' "
                + "and tc.table_name in ('" + String.join("','", NEW).toUpperCase() + "')"))
                .hasSize(8).allSatisfy(row -> assertThat(row.get("DELETE_RULE")).isIn("NO ACTION", "RESTRICT"));
            assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints "
                + "where constraint_schema='PUBLIC' and constraint_name='UQ_ARCH_CHECK_TYPE' "
                + "and constraint_type='UNIQUE'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where "
                + "table_schema='PUBLIC' and table_name='PROJECT_SECURITY_RISKS' and column_name='LEVEL'",
                Integer.class)).isZero();
            for (String table : NEW) assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"without-v003", "release-type", "milestone-fk", "extra-column", "board-cascade",
        "board-index", "version-default", "partial-table", "partial-marker", "already-migrated", "wrong-pk"})
    void incompatiblePartialAndRepeatedSchemasStopBeforeDdl(String variant) throws Exception {
        try (Connection connection = setup(memoryUrl(), !variant.equals("without-v003"))) {
            JdbcTemplate jdbc = jdbc(connection);
            switch (variant) {
                case "release-type" -> jdbc.execute("alter table release_trains alter column readiness bigint not null");
                case "milestone-fk" -> jdbc.execute("alter table project_milestones drop constraint fk_project_milestones_project");
                case "extra-column" -> jdbc.execute("alter table projects add column unexpected integer");
                case "board-cascade" -> {
                    jdbc.execute("alter table board_cards drop constraint fk_board_cards_project");
                    jdbc.execute("alter table board_cards add constraint fk_board_cards_project foreign key(project_id) "
                        + "references projects(id) on delete cascade");
                }
                case "board-index" -> {
                    jdbc.execute("alter table board_cards drop constraint fk_board_cards_project");
                    jdbc.execute("drop index idx_board_cards_project");
                    jdbc.execute("create index idx_board_cards_project on board_cards(project_id,title)");
                    jdbc.execute("alter table board_cards add constraint fk_board_cards_project foreign key(project_id) "
                        + "references projects(id) on delete no action");
                }
                case "version-default" -> jdbc.execute("alter table projects alter column version drop default");
                case "partial-table" -> jdbc.execute("create table project_architecture_components (id varchar(36) primary key)");
                case "partial-marker" -> jdbc.execute("create table pm_architecture_schema_migrations "
                    + "(migration_version varchar(16) not null primary key, applied_at timestamp with time zone not null)");
                case "already-migrated" -> script(connection, "V004__architecture_security.sql");
                case "wrong-pk" -> {
                    jdbc.execute("alter table release_trains drop primary key");
                    jdbc.execute("alter table release_trains add primary key(id,name)");
                }
                default -> { }
            }
            var dataBefore = snapshot(jdbc, OLD);
            var schemaBefore = schema(connection);
            assertThatThrownBy(() -> script(connection, "V004__architecture_security.sql"))
                .isInstanceOf(ScriptException.class)
                .hasStackTraceContaining("PM003B1B_PRECONDITION_FAILED_EXPECTED_V003_SCHEMA");
            assertThat(snapshot(jdbc, OLD)).isEqualTo(dataBefore);
            assertThat(schema(connection)).isEqualTo(schemaBefore);
        }
    }

    @Test
    void allRepositoriesPersistNullableFieldsAndSetsAndReadsDoNotWrite() throws Exception {
        String url = memoryUrl();
        try (Connection connection = setup(url, true)) { script(connection, "V004__architecture_security.sql"); script(connection, "V005__architecture_passport_instance.sql"); }
        try (var context = new SpringApplicationBuilder(ProjectManagerApplication.class)
            .web(WebApplicationType.NONE).run("--spring.datasource.url=" + url,
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.profiles.active=default", "--logging.level.root=ERROR")) {
            var passports = context.getBean(ArchitecturePassportRepository.class);
            var dataClasses = context.getBean(ArchitectureDataClassRepository.class);
            var environments = context.getBean(ArchitectureEnvironmentRepository.class);
            var components = context.getBean(ArchitectureComponentRepository.class);
            var integrations = context.getBean(ArchitectureIntegrationRepository.class);
            var integrationClasses = context.getBean(IntegrationDataClassRepository.class);
            var checks = context.getBean(ArchitectureCheckRepository.class);
            var risks = context.getBean(SecurityRiskRepository.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            String componentId = UUID.randomUUID().toString();
            String integrationId = UUID.randomUUID().toString();
            String checkId = UUID.randomUUID().toString();
            String riskId = UUID.randomUUID().toString();
            tx.executeWithoutResult(status -> {
                ArchitecturePassportEntity passport = new ArchitecturePassportEntity(); passport.setInstanceId(UUID.randomUUID().toString());
                passport.setProject(context.getBean(ProjectRepository.class).findById("zero").orElseThrow());
                passport.setCriticality("HIGH"); passport.setStage("IN_DESIGN"); passport.setSolutionType("NEW_SOLUTION");
                passport.setCreatedAt(Instant.parse("2026-10-14T10:00:00Z"));
                passport.setUpdatedAt(Instant.parse("2026-10-14T10:00:00Z"));
                passports.saveAndFlush(passport);
                for (String code : List.of("PERSONAL", "INTERNAL")) {
                    ArchitectureDataClassEntity item = new ArchitectureDataClassEntity();
                    item.setId(new ArchitectureDataClassId("zero", code)); item.setPassport(passport);
                    dataClasses.saveAndFlush(item);
                }
                for (String code : List.of("TEST", "PROD")) {
                    ArchitectureEnvironmentEntity item = new ArchitectureEnvironmentEntity();
                    item.setId(new ArchitectureEnvironmentId("zero", code)); item.setPassport(passport);
                    environments.saveAndFlush(item);
                }
                ArchitectureComponentEntity component = new ArchitectureComponentEntity();
                component.setId(componentId); component.setPassport(passport); component.setName("Application");
                component.setComponentType("BACKEND"); component.setCriticality("HIGH"); component.setPosition(0);
                components.saveAndFlush(component);
                ArchitectureIntegrationEntity integration = new ArchitectureIntegrationEntity();
                integration.setId(integrationId); integration.setPassport(passport); integration.setName("Business exchange");
                integration.setSourceSystem("Source"); integration.setTargetSystem("Target");
                integration.setDirection("OUTBOUND"); integration.setInteractionType("API");
                integration.setAuthCategory("SERVICE_ACCOUNT"); integration.setCriticality("MEDIUM");
                integration.setReadiness("PLANNED"); integrations.saveAndFlush(integration);
                for (String code : List.of("PUBLIC", "INTERNAL")) {
                    IntegrationDataClassEntity item = new IntegrationDataClassEntity();
                    item.setId(new IntegrationDataClassId(integrationId, code)); item.setIntegration(integration);
                    integrationClasses.saveAndFlush(item);
                }
                ArchitectureCheckEntity check = new ArchitectureCheckEntity();
                check.setId(checkId); check.setPassport(passport); check.setCheckType("THREAT_MODEL");
                check.setRequired(true); check.setStatus("NOT_STARTED"); checks.saveAndFlush(check);
                SecurityRiskEntity risk = new SecurityRiskEntity();
                risk.setId(riskId); risk.setPassport(passport); risk.setName("Availability concern");
                risk.setCategory("AVAILABILITY"); risk.setProbability("LOW"); risk.setImpact("HIGH");
                risk.setStatus("OPEN"); risks.saveAndFlush(risk);
            });
            var beforeReads = allRows(jdbc);
            TransactionTemplate read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            read.setReadOnly(true);
            for (int attempt = 0; attempt < 3; attempt++) {
                read.executeWithoutResult(status -> {
                    ArchitecturePassportEntity p = passports.findById("zero").orElseThrow();
                    assertThat(p.getVersion()).isZero(); assertThat(p.getArchitect()).isNull();
                    assertThat(p.getSolutionSummary()).isNull(); assertThat(p.getLastReviewDate()).isNull();
                    assertThat(p.getAvailabilityRequirement()).isNull(); assertThat(p.getRecoveryRequirement()).isNull();
                    assertThat(p.getCreatedAt()).isEqualTo(Instant.parse("2026-10-14T10:00:00Z"));
                    assertThat(p.getUpdatedAt()).isEqualTo(p.getCreatedAt());
                    assertThat(dataClasses.findAll()).extracting(v -> v.getId().getClassCode())
                        .containsExactlyInAnyOrder("PERSONAL", "INTERNAL");
                    assertThat(environments.findAll()).extracting(v -> v.getId().getEnvironmentCode())
                        .containsExactlyInAnyOrder("TEST", "PROD");
                    assertThat(integrationClasses.findAll()).extracting(v -> v.getId().getClassCode())
                        .containsExactlyInAnyOrder("PUBLIC", "INTERNAL");
                    var component = components.findById(componentId).orElseThrow();
                    assertThat(component.getPurpose()).isNull(); assertThat(component.getPlatform()).isNull();
                    assertThat(component.getVersion()).isZero();
                    var integration = integrations.findById(integrationId).orElseThrow();
                    assertThat(integration.getComment()).isNull(); assertThat(integration.getResponsibleParty()).isNull();
                    assertThat(integration.getVersion()).isZero();
                    var check = checks.findById(checkId).orElseThrow();
                    assertThat(check.getOwner()).isNull(); assertThat(check.getComment()).isNull();
                    assertThat(check.getActualDate()).isNull(); assertThat(check.getPlannedDate()).isNull();
                    assertThat(check.getVersion()).isZero();
                    var risk = risks.findById(riskId).orElseThrow();
                    assertThat(risk.getDescription()).isNull(); assertThat(risk.getOwner()).isNull();
                    assertThat(risk.getTreatmentDueDate()).isNull(); assertThat(risk.getTreatmentPlan()).isNull();
                    assertThat(risk.getClosedDate()).isNull(); assertThat(risk.getAcceptanceJustification()).isNull();
                    assertThat(risk.getVersion()).isZero();
                });
                assertThat(allRows(jdbc)).isEqualTo(beforeReads);
            }
            // JPA and the physical FK both reject deleting a passport with children.
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                passports.deleteById("zero"); passports.flush();
            })).isInstanceOf(Exception.class);
            assertThat(allRows(jdbc)).isEqualTo(beforeReads);
            tx.executeWithoutResult(status -> {
                components.findById(componentId).orElseThrow().setName("Changed component"); components.flush();
            });
            assertThat(components.findById(componentId).orElseThrow().getVersion()).isEqualTo(1L);
            assertThat(passports.findById("zero").orElseThrow().getVersion()).isZero();
            assertPhysicalRestrictions(jdbc, integrationId);
        }
    }

    private static void assertPhysicalRestrictions(JdbcTemplate jdbc, String integrationId) {
        var before = allRows(jdbc);
        for (String table : NEW) {
            String column = table.equals("project_integration_data_classes") ? "integration_id" : "project_id";
            assertThatThrownBy(() -> jdbc.update("update " + table + " set " + column + "='absent'"))
                .isInstanceOf(Exception.class);
            assertThat(allRows(jdbc)).isEqualTo(before);
        }
        for (String table : List.of("project_architecture_passports", "project_architecture_components",
            "project_architecture_integrations", "project_architecture_checks", "project_security_risks")) {
            assertThatThrownBy(() -> jdbc.update("update " + table + " set version=-1")).isInstanceOf(Exception.class);
        }
        Map<String, String> catalogColumns = Map.of(
            "project_architecture_passports", "stage", "project_architecture_data_classes", "class_code",
            "project_architecture_environments", "environment_code", "project_architecture_components", "component_type",
            "project_architecture_integrations", "direction", "project_integration_data_classes", "class_code",
            "project_architecture_checks", "status", "project_security_risks", "probability");
        catalogColumns.forEach((table, column) -> assertThatThrownBy(() -> jdbc.update(
            "update " + table + " set " + column + "='INVALID'")).isInstanceOf(Exception.class));
        assertThatThrownBy(() -> jdbc.update("update project_architecture_components set position=-1"))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("update project_architecture_components set name=null"))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("update project_architecture_components set id=?", "x".repeat(37)))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("insert into project_architecture_data_classes values ('zero','INTERNAL')"))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status) "
            + "values (?, 'zero', 'THREAT_MODEL',true,'NOT_STARTED')", UUID.randomUUID().toString()))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("delete from projects where id='zero'")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("delete from project_architecture_passports where project_id='zero'"))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("delete from project_architecture_integrations where id=?", integrationId))
            .isInstanceOf(Exception.class);
        assertThat(allRows(jdbc)).isEqualTo(before);
    }

    static Connection setup(String url, boolean v003) throws Exception {
        Connection connection = DriverManager.getConnection(url, "sa", "");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("pm002a-old-schema.sql"));
        JdbcTemplate jdbc = jdbc(connection);
        for (String id : List.of("zero", "max")) {
            jdbc.update("insert into projects(id,name,owner,status,health,delivery_model,budget,progress,quarter,deadline,"
                + "milestone,risk,dependency,kpi_name,kpi_target,summary) values (?,?,'Owner','active','green',"
                + "'kanban',?,?,'Q1','2027-01-01','M','R','D','K','T','Preserve')", id, id,
                id.equals("zero") ? 0 : Integer.MAX_VALUE, id.equals("zero") ? 0 : 73);
        }
        jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date,"
            + "priority,labels,estimate,blocked) values ('card','zero','custom',47,'Title','Description','Owner',"
            + "'2027-01-02','custom','tag',13,true)");
        jdbc.update("insert into release_trains(id,name,status,cadence,quarter,planned_release_date,code_freeze_date,"
            + "qa_freeze_date,go_live_date,capacity_points,committed_points,readiness,blocked_items,scope,risk,decision) "
            + "values ('train','Name','custom','monthly','Q1','2027-01-01','2026-12-01','2026-12-15',"
            + "'2027-01-01',100,80,50,2,'Scope','Risk','Decision')");
        List<String> oldColumns = jdbc.queryForList("select column_name from information_schema.columns where "
            + "table_schema='PUBLIC' and table_name='PROJECTS' order by ordinal_position", String.class);
        String projection = String.join(",", oldColumns);
        var beforeV002 = jdbc.queryForList("select " + projection + " from projects order by id");
        var cardsBefore = jdbc.queryForList("select * from board_cards order by id");
        var trainsBefore = jdbc.queryForList("select * from release_trains order by id");
        script(connection, "V002__project_model_and_milestones.sql");
        assertThat(jdbc.queryForList("select " + projection + " from projects order by id")).isEqualTo(beforeV002);
        assertThat(jdbc.queryForList("select * from board_cards order by id")).isEqualTo(cardsBefore);
        assertThat(jdbc.queryForList("select * from release_trains order by id")).isEqualTo(trainsBefore);
        jdbc.update("update projects set version=9,start_date=DATE '2026-09-01' where id='max'");
        jdbc.update("insert into project_milestones(id,project_id,name,planned_date,completed,completed_date,position) "
            + "values ('point','max','Milestone',DATE '2026-10-01',true,DATE '2026-10-02',4)");
        if (v003) script(connection, "V003__board_cards_project_fk.sql");
        return connection;
    }

    static void script(Connection connection, String file) {
        ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/manual/" + file));
    }

    static Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc, List<String> tables) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : tables) result.put(table, jdbc.queryForList("select * from " + table + " order by 1,2"));
        return result;
    }

    static Map<String, List<Map<String, Object>>> allRows(JdbcTemplate jdbc) {
        List<String> tables = new ArrayList<>(OLD); tables.addAll(NEW); tables.add("pm_architecture_schema_migrations");
        return snapshot(jdbc, tables);
    }

    static List<String> schema(Connection connection) throws Exception {
        List<String> lines = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SCRIPT NODATA")) {
            while (rows.next()) lines.add(rows.getString(1));
        }
        return lines;
    }

    static JdbcTemplate jdbc(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    static String memoryUrl() {
        return "jdbc:h2:mem:pmtest_archschema_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }
}
