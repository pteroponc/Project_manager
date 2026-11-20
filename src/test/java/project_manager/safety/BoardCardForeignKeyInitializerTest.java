package project_manager.safety;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.mock.env.MockEnvironment;
import project_manager.ProjectManagerApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardCardForeignKeyInitializerTest {
    @ParameterizedTest
    @ValueSource(strings = {"default", "demo", "smoke"})
    void generatedSchemasEnforceTheForeignKeyBeforeApplicationRunners(String profile) throws Exception {
        var checkedBeforeRunners = new AtomicBoolean();
        try (var context = new SpringApplicationBuilder(ProjectManagerApplication.class)
                .web(WebApplicationType.NONE)
                .listeners((ApplicationListener<ApplicationStartedEvent>) event -> {
                    try (var connection = event.getApplicationContext().getBean(DataSource.class).getConnection()) {
                        assertForeignKey(connection);
                        assertNullable(connection);
                        assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS")).isZero();
                        checkedBeforeRunners.set(true);
                    } catch (SQLException error) {
                        throw new IllegalStateException(error);
                    }
                })
                .run("--spring.profiles.active=" + profile, "--spring.datasource.url=" + memoryUrl(),
                    "--spring.main.lazy-initialization=true", "--release-checks.enabled=false",
                    "--logging.level.root=ERROR")) {
            assertThat(checkedBeforeRunners).isTrue();
            if ("demo".equals(profile)) {
                try (var connection = context.getBean(DataSource.class).getConnection()) {
                    assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS")).isPositive();
                }
            }
        }
    }

    @Test
    void creationIsIdempotentAndRestrictsWritesWithoutChangingRows() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, true);
            execute(connection, "INSERT INTO BOARD_CARDS VALUES ('card', 'parent')");
            initialize(url, "update");
            initialize(url, "create-drop");
            initialize(url, "validate");
            assertForeignKey(connection);
            assertNullable(connection);
            execute(connection, "INSERT INTO BOARD_CARDS VALUES ('personal', NULL)");
            execute(connection, "INSERT INTO PROJECTS VALUES ('empty')");
            execute(connection, "DELETE FROM PROJECTS WHERE ID='empty'");

            for (String sql : new String[]{
                    "INSERT INTO BOARD_CARDS VALUES ('other', 'missing')",
                    "UPDATE BOARD_CARDS SET PROJECT_ID='missing' WHERE ID='card'",
                    "DELETE FROM PROJECTS WHERE ID='parent'",
                    "UPDATE PROJECTS SET ID='other' WHERE ID='parent'"}) {
                assertThatThrownBy(() -> execute(connection, sql)).isInstanceOf(SQLException.class);
                assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS")).isEqualTo(2);
                assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS WHERE ID='card' AND PROJECT_ID='parent'")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM PROJECTS WHERE ID='parent'")).isEqualTo(1);
            }
            assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS WHERE ID='personal' AND PROJECT_ID IS NULL"))
                .isEqualTo(1);
        }
    }

    @Test
    void orphanReferencesPreventDdl() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, false);
            execute(connection, "INSERT INTO BOARD_CARDS VALUES ('card', 'missing')");
            assertThatThrownBy(() -> initialize(url, "update")).hasMessageContaining("orphan project");
            assertNoForeignKey(connection);
            assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS")).isEqualTo(1);
            assertNullable(connection);
        }
    }

    @Test
    void existingPersonalCardSurvivesNullableAlterationAndFkCreation() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, false);
            execute(connection, "INSERT INTO BOARD_CARDS VALUES ('personal', NULL)");
            execute(connection, "INSERT INTO BOARD_CARDS VALUES ('project', 'parent')");
            initialize(url, "update");
            initialize(url, "update");
            assertNullable(connection);
            assertForeignKey(connection);
            assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS")).isEqualTo(2);
            assertThat(count(connection, "SELECT COUNT(*) FROM BOARD_CARDS WHERE ID='personal' AND PROJECT_ID IS NULL"))
                .isEqualTo(1);
        }
    }

    @Test
    void incompatibleExistingForeignKeyFailsWithoutReplacingIt() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, true);
            execute(connection, "ALTER TABLE BOARD_CARDS ADD CONSTRAINT EXISTING_FK FOREIGN KEY (PROJECT_ID) REFERENCES PROJECTS (ID) ON DELETE CASCADE");
            assertThatThrownBy(() -> initialize(url, "update")).hasMessageContaining("incompatible existing");
            try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "BOARD_CARDS")) {
                assertThat(keys.next()).isTrue();
                assertThat(keys.getString("FK_NAME")).isEqualTo("EXISTING_FK");
                assertThat(keys.next()).isFalse();
            }
        }
    }

    @Test
    void validateModeRejectsMissingForeignKeyWithoutDdl() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, true);
            assertThatThrownBy(() -> initialize(url, "validate")).hasMessageContaining("must allow NULL in validate");
            assertNoForeignKey(connection);
            assertNotNullable(connection);
        }
    }

    @Test
    void validateModeRejectsMissingForeignKeyOnNullableColumnWithoutDdl() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, false);
            assertThatThrownBy(() -> initialize(url, "validate")).hasMessageContaining("missing in validate");
            assertNoForeignKey(connection);
            assertNullable(connection);
        }
    }

    @Test
    void validateModeRejectsExistingFkOnNotNullableColumnWithoutDdl() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, true);
            execute(connection, "ALTER TABLE BOARD_CARDS ADD CONSTRAINT EXISTING_FK FOREIGN KEY (PROJECT_ID) REFERENCES PROJECTS (ID) ON DELETE NO ACTION ON UPDATE NO ACTION");
            assertThatThrownBy(() -> initialize(url, "validate")).hasMessageContaining("must allow NULL in validate");
            assertNotNullable(connection);
            assertForeignKey(connection);
        }
    }

    @Test
    void orphanOnNotNullableColumnPreventsNullableAlteration() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, true);
            execute(connection, "INSERT INTO BOARD_CARDS VALUES ('orphan', 'missing')");
            assertThatThrownBy(() -> initialize(url, "update")).hasMessageContaining("orphan project");
            assertNotNullable(connection);
            assertNoForeignKey(connection);
        }
    }

    @Test
    void incompatibleFkDoesNotTriggerNullableAlteration() throws Exception {
        String url = memoryUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            setup(connection, true);
            execute(connection, "ALTER TABLE BOARD_CARDS ADD CONSTRAINT EXISTING_FK FOREIGN KEY (PROJECT_ID) REFERENCES PROJECTS (ID) ON DELETE CASCADE");
            assertThatThrownBy(() -> initialize(url, "update")).hasMessageContaining("incompatible existing");
            assertNotNullable(connection);
        }
    }

    private static String memoryUrl() {
        return "jdbc:h2:mem:pmtest_fk_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }

    private static void initialize(String url, String mode) throws SQLException {
        var source = new JdbcDataSource();
        source.setURL(url);
        source.setUser("sa");
        new BoardCardForeignKeyInitializer(source, new MockEnvironment()
            .withProperty("spring.datasource.url", url)
            .withProperty("spring.jpa.hibernate.ddl-auto", mode)).afterPropertiesSet();
    }

    private static void setup(Connection connection, boolean required) throws SQLException {
        execute(connection, "CREATE TABLE PROJECTS (ID VARCHAR(255) PRIMARY KEY)");
        execute(connection, "CREATE TABLE BOARD_CARDS (ID VARCHAR(255) PRIMARY KEY, PROJECT_ID VARCHAR(255)"
            + (required ? " NOT NULL)" : ")"));
        execute(connection, "INSERT INTO PROJECTS VALUES ('parent')");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void assertForeignKey(Connection connection) throws SQLException {
        try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "BOARD_CARDS")) {
            assertThat(keys.next()).isTrue();
            assertThat(keys.getString("FKCOLUMN_NAME")).isEqualTo("PROJECT_ID");
            assertThat(keys.getString("PKTABLE_NAME")).isEqualTo("PROJECTS");
            assertThat(keys.getString("PKCOLUMN_NAME")).isEqualTo("ID");
            assertThat(keys.getInt("DELETE_RULE")).isIn(1, 3);
            assertThat(keys.getInt("UPDATE_RULE")).isIn(1, 3);
            assertThat(keys.next()).isFalse();
        }
    }

    private static void assertNoForeignKey(Connection connection) throws SQLException {
        try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "BOARD_CARDS")) {
            assertThat(keys.next()).isFalse();
        }
    }

    private static void assertNullable(Connection connection) throws SQLException {
        assertThat(projectColumnNullable(connection)).isEqualTo(DatabaseMetaData.columnNullable);
    }

    private static void assertNotNullable(Connection connection) throws SQLException {
        assertThat(projectColumnNullable(connection)).isEqualTo(DatabaseMetaData.columnNoNulls);
    }

    private static int projectColumnNullable(Connection connection) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(null, "PUBLIC", "BOARD_CARDS", "PROJECT_ID")) {
            assertThat(columns.next()).isTrue();
            return columns.getInt("NULLABLE");
        }
    }
}
