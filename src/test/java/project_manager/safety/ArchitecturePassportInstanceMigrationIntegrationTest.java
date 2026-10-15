package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import project_manager.ProjectManagerApplication;
import project_manager.service.ArchitectureReadService;
import project_manager.service.ArchitecturePassportDeletionService;
import project_manager.service.ArchitecturePassportMutationService;
import project_manager.web.ArchitectureMutationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static project_manager.safety.ArchitectureSchemaMigrationIntegrationTest.*;

class ArchitecturePassportInstanceMigrationIntegrationTest {
    private static final String V005="V005__architecture_passport_instance.sql";

    private static void fillV004(Connection connection) {
        script(connection,"V004__architecture_security.sql");
        JdbcTemplate jdbc=jdbc(connection);
        for (String id : new String[]{"zero","max"})
            jdbc.update("insert into project_architecture_passports(project_id,criticality,stage,solution_type,architect,version,created_at,updated_at) "
                + "values (?,'HIGH','NOT_STARTED','NEW_SOLUTION',null,4,'2026-01-01T00:00:00Z','2026-02-01T00:00:00Z')",id);
        jdbc.update("insert into project_architecture_components(id,project_id,name,component_type,criticality,external_component,position,version) values ('component','max','Keep','BACKEND','LOW',false,0,3)");
        jdbc.update("insert into project_architecture_integrations(id,project_id,name,source_system,target_system,direction,interaction_type,auth_category,criticality,readiness,version) values ('integration','max','Keep','Source','Target','OUTBOUND','API','NONE','LOW','READY',2)");
        jdbc.update("insert into project_integration_data_classes(integration_id,class_code) values ('integration','PUBLIC')");
        jdbc.update("insert into project_architecture_checks(id,project_id,check_type,required,status,version) values ('check','max','THREAT_MODEL',true,'NOT_STARTED',1)");
        jdbc.update("insert into project_security_risks(id,project_id,name,category,probability,impact,status,version) values ('risk','max','Keep','DATA','LOW','LOW','OPEN',0)");
        jdbc.update("insert into project_architecture_data_classes(project_id,class_code) values ('max','PERSONAL')");
        jdbc.update("insert into project_architecture_environments(project_id,environment_code) values ('max','TEST')");
    }

    @Test
    void filledMigrationPreservesEveryOldValueAndAddsUniqueMandatoryUuid() throws Exception {
        try (Connection connection=setup(memoryUrl(),true)) {
            fillV004(connection); var jdbc=jdbc(connection); var before=allRows(jdbc);
            script(connection,V005);
            var after=allRows(jdbc);
            after.get("project_architecture_passports").forEach(row -> row.remove("INSTANCE_ID"));
            after.put("pm_architecture_schema_migrations",after.get("pm_architecture_schema_migrations").stream().filter(row -> row.get("MIGRATION_VERSION").equals("V004")).toList());
            assertThat(after).isEqualTo(before);
            var ids=jdbc.queryForList("select instance_id from project_architecture_passports",String.class);
            assertThat(ids).hasSize(2).doesNotHaveDuplicates(); ids.forEach(UUID::fromString);
            assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema='PUBLIC' and table_name='PROJECT_ARCHITECTURE_PASSPORTS' and column_name='INSTANCE_ID'",String.class)).isEqualTo("NO");
            var snapshot=allRows(jdbc);
            assertThatThrownBy(() -> jdbc.update("update project_architecture_passports set instance_id=null where project_id='zero'")).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> jdbc.update("update project_architecture_passports set instance_id=(select instance_id from project_architecture_passports where project_id='max') where project_id='zero'")).isInstanceOf(Exception.class);
            assertThat(allRows(jdbc)).isEqualTo(snapshot);
        }
    }

    @ParameterizedTest @ValueSource(strings={"partial-column","marker","repeat","type","fk","index","extra-column","without-v004"})
    void refusesIncompatiblePartialAndRepeatedSchemasBeforeDdlOrRowChanges(String variant) throws Exception {
        try (Connection connection=setup(memoryUrl(),true)) {
            if (!variant.equals("without-v004")) fillV004(connection);
            var jdbc=jdbc(connection);
            switch (variant) {
                case "partial-column" -> jdbc.execute("alter table project_architecture_passports add column instance_id varchar(36)");
                case "marker" -> jdbc.update("insert into pm_architecture_schema_migrations values ('V005',CURRENT_TIMESTAMP)");
                case "repeat" -> script(connection,V005);
                case "type" -> jdbc.execute("alter table project_architecture_passports alter column architect varchar(256)");
                case "fk" -> jdbc.execute("alter table project_security_risks drop constraint fk_security_risk_passport");
                case "index" -> jdbc.execute("drop index idx_arch_passport_portfolio");
                case "extra-column" -> jdbc.execute("alter table projects add column unexpected integer");
                default -> { }
            }
            var schemaBefore=schema(connection);
            var dataBefore=variant.equals("without-v004")?snapshot(jdbc,java.util.List.of("projects","board_cards","release_trains","project_milestones")):allRows(jdbc);
            assertThatThrownBy(() -> script(connection,V005)).isInstanceOf(Exception.class);
            assertThat(schema(connection)).isEqualTo(schemaBefore);
            assertThat(variant.equals("without-v004")?snapshot(jdbc,java.util.List.of("projects","board_cards","release_trains","project_milestones")):allRows(jdbc)).isEqualTo(dataBefore);
        }
    }

    @Test
    void restartValidatesMigratedSchemaAndRetainsInstanceAndRejectsOldInstanceAfterRecreation() throws Exception {
        String url=memoryUrl(); String instanceB; String instanceA;
        try (Connection connection=setup(url,true)) { fillV004(connection); script(connection,V005); }
        var json=new ObjectMapper();
        try (var context=start(url)) {
            var reads=context.getBean(ArchitectureReadService.class);
            instanceA=reads.card("zero").passport().instanceId();
            context.getBean(ArchitecturePassportDeletionService.class).delete("zero",Map.of("expectedVersion",new String[]{"4"},"confirmed",new String[]{"true"},"expectedPassportInstanceId",new String[]{instanceA}));
            var b=context.getBean(ArchitecturePassportMutationService.class).create("zero",json.readTree("{\"expectedProjectVersion\":1,\"criticality\":\"HIGH\",\"stage\":\"NOT_STARTED\",\"solutionType\":\"NEW_SOLUTION\"}"));
            instanceB=b.passport().instanceId(); assertThat(instanceB).isNotEqualTo(instanceA);
        }
        try (var context=start(url)) {
            var reads=context.getBean(ArchitectureReadService.class);
            assertThat(reads.card("zero").passport().instanceId()).isEqualTo(instanceB);
            var jdbc=context.getBean(JdbcTemplate.class); var before=allRows(jdbc);
            var mutations=context.getBean(ArchitecturePassportMutationService.class);
            assertThatThrownBy(() -> mutations.patch("zero",json.readTree("{\"expectedVersion\":0,\"expectedPassportInstanceId\":\""+instanceA+"\",\"architect\":\"Stale\"}")))
                .isInstanceOfSatisfying(ArchitectureMutationException.class,error -> assertThat(error.code()).isEqualTo("PASSPORT_INSTANCE_CONFLICT"));
            assertThat(allRows(jdbc)).isEqualTo(before);
            assertThat(mutations.patch("zero",json.readTree("{\"expectedVersion\":0,\"expectedPassportInstanceId\":\""+instanceB+"\",\"architect\":\"Current\"}")).passport().instanceId()).isEqualTo(instanceB);
        }
    }
    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(ProjectManagerApplication.class).web(WebApplicationType.NONE).run("--spring.datasource.url="+url,
            "--spring.jpa.hibernate.ddl-auto=validate","--spring.profiles.active=default","--logging.level.root=ERROR");
    }
}
