package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.init.ScriptException;

import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static project_manager.safety.ArchitectureSchemaMigrationIntegrationTest.*;

class BoardCardVersionMigrationIntegrationTest {
    private static final String V006 = "V006__board_card_version.sql";

    @Test
    void existingCardAndProjectLinksSurviveAndReceiveVersionZero() throws Exception {
        try (Connection connection = v005()) {
            var jdbc = jdbc(connection);
            var before = allRows(jdbc);
            script(connection, V006);
            var after = allRows(jdbc);
            after.get("board_cards").forEach(row -> row.remove("VERSION"));
            assertThat(after).isEqualTo(before);
            assertThat(jdbc.queryForObject("select version from board_cards where id='card'", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints "
                + "where table_schema='PUBLIC' and table_name='BOARD_CARDS' and constraint_name='FK_BOARD_CARDS_PROJECT'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes "
                + "where table_schema='PUBLIC' and table_name='BOARD_CARDS' and index_name='IDX_BOARD_CARDS_PROJECT'", Integer.class)).isEqualTo(1);
            jdbc.update("insert into board_cards(id,project_id,column_key,position,title,description,owner,due_date) "
                + "values ('new-card','zero','backlog',0,'New','Description','Owner','2027-01-02')");
            assertThat(jdbc.queryForObject("select version from board_cards where id='new-card'", Long.class)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"repeat", "partial-column", "wrong-type", "extra-column", "missing-fk", "missing-index", "cascade-fk", "wrong-index"})
    void repeatedAndIncompatibleSchemasFailBeforeAnyChange(String variant) throws Exception {
        try (Connection connection = v005()) {
            var jdbc = jdbc(connection);
            switch (variant) {
                case "repeat" -> script(connection, V006);
                case "partial-column" -> jdbc.execute("alter table board_cards add column version bigint");
                case "wrong-type" -> jdbc.execute("alter table board_cards alter column title varchar(256)");
                case "extra-column" -> jdbc.execute("alter table board_cards add column unexpected integer");
                case "missing-fk" -> jdbc.execute("alter table board_cards drop constraint fk_board_cards_project");
                case "missing-index" -> {
                    jdbc.execute("alter table board_cards drop constraint fk_board_cards_project");
                    jdbc.execute("drop index idx_board_cards_project");
                    jdbc.execute("alter table board_cards add constraint fk_board_cards_project foreign key(project_id) references projects(id)");
                }
                case "cascade-fk" -> {
                    jdbc.execute("alter table board_cards drop constraint fk_board_cards_project");
                    jdbc.execute("alter table board_cards add constraint fk_board_cards_project foreign key(project_id) "
                        + "references projects(id) on delete cascade");
                }
                case "wrong-index" -> {
                    jdbc.execute("alter table board_cards drop constraint fk_board_cards_project");
                    jdbc.execute("drop index idx_board_cards_project");
                    jdbc.execute("create index idx_board_cards_project on board_cards(project_id,title)");
                    jdbc.execute("alter table board_cards add constraint fk_board_cards_project foreign key(project_id) references projects(id)");
                }
                default -> throw new AssertionError(variant);
            }
            List<String> schemaBefore = schema(connection);
            var rowsBefore = allRows(jdbc);
            assertThatThrownBy(() -> script(connection, V006)).isInstanceOf(ScriptException.class);
            assertThat(schema(connection)).isEqualTo(schemaBefore);
            assertThat(allRows(jdbc)).isEqualTo(rowsBefore);
        }
    }

    private Connection v005() throws Exception {
        Connection connection = setup(memoryUrl(), true);
        script(connection, "V004__architecture_security.sql");
        script(connection, "V005__architecture_passport_instance.sql");
        return connection;
    }
}
