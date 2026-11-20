package project_manager.safety;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
@DependsOn("entityManagerFactory")
public class BoardCardForeignKeyInitializer implements InitializingBean {
    private static final Pattern MEMORY_URL = Pattern.compile(
        "jdbc:h2:mem:pm(demo|smoke|test)_[A-Za-z0-9_-]+(;MODE=PostgreSQL)?(;DB_CLOSE_DELAY=-1)?");

    private final DataSource dataSource;
    private final Environment environment;

    public BoardCardForeignKeyInitializer(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:h2:mem:")) return;
        if (!MEMORY_URL.matcher(url).matches()) throw invalid("unapproved in-memory URL");

        String mode = environment.getProperty("spring.jpa.hibernate.ddl-auto", "");
        if (!Set.of("create-drop", "update", "validate").contains(mode)) {
            throw invalid("unsupported Hibernate schema mode");
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String actual = metadata.getURL();
            if (!"H2".equals(metadata.getDatabaseProductName()) || actual == null
                    || !MEMORY_URL.matcher(actual).matches()
                    || !url.split(";", 2)[0].equals(actual.split(";", 2)[0])) {
                throw invalid("unexpected JDBC connection");
            }
            ensureForeignKey(connection, !"validate".equals(mode));
        }
    }

    private static void ensureForeignKey(Connection connection, boolean allowDdl) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Column parent = column(metadata, "PROJECTS", "ID");
        Column child = column(metadata, "BOARD_CARDS", "PROJECT_ID");
        if (parent.type() != Types.VARCHAR || child.type() != parent.type() || child.size() != parent.size()
                || parent.nullable() != DatabaseMetaData.columnNoNulls) {
            throw invalid("incompatible project key columns");
        }
        try (var keys = metadata.getPrimaryKeys(null, "PUBLIC", "PROJECTS")) {
            if (!keys.next() || !"ID".equals(keys.getString("COLUMN_NAME")) || keys.next()) {
                throw invalid("PROJECTS.ID must be the single-column primary key");
            }
        }
        boolean exists = hasCompatibleForeignKey(metadata);
        try (var statement = connection.createStatement()) {
            try (var invalidCards = statement.executeQuery("""
                SELECT COUNT(*) FROM PUBLIC.BOARD_CARDS B
                LEFT JOIN PUBLIC.PROJECTS P ON P.ID = B.PROJECT_ID
                WHERE B.PROJECT_ID IS NOT NULL AND P.ID IS NULL
                """)) {
                invalidCards.next();
                if (invalidCards.getLong(1) != 0) throw invalid("orphan project references");
            }
            if (child.nullable() == DatabaseMetaData.columnNullableUnknown) {
                throw invalid("unknown BOARD_CARDS.PROJECT_ID nullability");
            }
            if (child.nullable() == DatabaseMetaData.columnNoNulls) {
                if (!allowDdl) throw invalid("BOARD_CARDS.PROJECT_ID must allow NULL in validate mode");
                // Only an approved in-memory H2 connection reaches this DDL.
                statement.executeUpdate("ALTER TABLE PUBLIC.BOARD_CARDS ALTER COLUMN PROJECT_ID DROP NOT NULL");
                if (column(metadata, "BOARD_CARDS", "PROJECT_ID").nullable()
                        != DatabaseMetaData.columnNullable) {
                    throw invalid("BOARD_CARDS.PROJECT_ID remains NOT NULL after alteration");
                }
            }
            if (exists) return;
            if (!allowDdl) throw invalid("required foreign key missing in validate mode");
            // H2 DDL can commit. All preconditions must pass before this statement.
            statement.executeUpdate("""
                ALTER TABLE PUBLIC.BOARD_CARDS ADD CONSTRAINT FK_BOARD_CARDS_PROJECT
                FOREIGN KEY (PROJECT_ID) REFERENCES PUBLIC.PROJECTS (ID)
                ON DELETE NO ACTION ON UPDATE NO ACTION
                """);
        }
        if (!hasCompatibleForeignKey(metadata)) throw invalid("foreign key missing after creation");
    }

    private static Column column(DatabaseMetaData metadata, String table, String name) throws SQLException {
        try (var columns = metadata.getColumns(null, "PUBLIC", table, name)) {
            if (!columns.next()) throw invalid("missing " + table + "." + name);
            return new Column(columns.getInt("DATA_TYPE"), columns.getInt("COLUMN_SIZE"),
                columns.getInt("NULLABLE"));
        }
    }

    private static boolean hasCompatibleForeignKey(DatabaseMetaData metadata) throws SQLException {
        Map<String, Integer> widths = new HashMap<>();
        Set<String> projectKeys = new HashSet<>();
        try (var keys = metadata.getImportedKeys(null, "PUBLIC", "BOARD_CARDS")) {
            while (keys.next()) {
                String name = keys.getString("FK_NAME");
                widths.merge(name, 1, Integer::sum);
                if (!"PROJECT_ID".equals(keys.getString("FKCOLUMN_NAME"))) continue;
                if (name == null || keys.getInt("KEY_SEQ") != 1
                        || !"PUBLIC".equals(keys.getString("PKTABLE_SCHEM"))
                        || !"PROJECTS".equals(keys.getString("PKTABLE_NAME"))
                        || !"ID".equals(keys.getString("PKCOLUMN_NAME"))
                        || !restrictive(keys.getInt("DELETE_RULE")) || !restrictive(keys.getInt("UPDATE_RULE"))) {
                    throw invalid("incompatible existing project foreign key");
                }
                projectKeys.add(name);
            }
        }
        for (String name : projectKeys) {
            if (widths.get(name) != 1) throw invalid("composite project foreign key");
        }
        return !projectKeys.isEmpty();
    }

    private static boolean restrictive(int rule) {
        return rule == DatabaseMetaData.importedKeyNoAction || rule == DatabaseMetaData.importedKeyRestrict;
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Board card foreign key initialization: " + reason);
    }

    private record Column(int type, int size, int nullable) {}
}
