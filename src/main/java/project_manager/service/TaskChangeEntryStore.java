package project_manager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Task-specific append-only history; task ID remains stable after the existing DELETE. */
@Component
@DependsOn("entityManagerFactory")
public class TaskChangeEntryStore implements InitializingBean {
    private static final Pattern MEMORY_URL = Pattern.compile(
        "jdbc:h2:mem:pm(demo|smoke|test)_[A-Za-z0-9_-]+(;MODE=PostgreSQL)?(;DB_CLOSE_DELAY=-1)?");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final Environment environment;

    public TaskChangeEntryStore(JdbcTemplate jdbc, ObjectMapper json,
                                @Qualifier("taskChangeClock") Clock clock, Environment environment) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:h2:mem:")) return;
        if (!MEMORY_URL.matcher(url).matches()) throw new IllegalStateException("Unapproved task history database");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS TASK_CHANGE_ENTRIES (
                SEQUENCE BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                ID VARCHAR(36) NOT NULL UNIQUE,
                TASK_ID VARCHAR(255) NOT NULL,
                OPERATION VARCHAR(32) NOT NULL,
                CHANGED_AT VARCHAR(40) NOT NULL,
                BEFORE_VERSION BIGINT,
                AFTER_VERSION BIGINT NOT NULL,
                BEFORE_JSON CLOB NOT NULL,
                AFTER_JSON CLOB NOT NULL,
                SOURCE VARCHAR(64) NOT NULL,
                INPUT_METHOD VARCHAR(64) NOT NULL,
                BASIS VARCHAR(1000),
                REASON VARCHAR(1000),
                CONFIRMATION VARCHAR(64),
                FORMAT_VERSION INT NOT NULL
            )
            """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS IDX_TASK_CHANGES_TASK ON TASK_CHANGE_ENTRIES (TASK_ID, SEQUENCE)");
    }

    public void append(String taskId, String operation, Long beforeVersion, long afterVersion,
                       Map<String, Object> before, Map<String, Object> after,
                       String source, String inputMethod, String confirmation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Task history requires a transaction");
        }
        if (before.isEmpty() && after.isEmpty()) return;
        try {
            jdbc.update("""
                INSERT INTO TASK_CHANGE_ENTRIES
                (ID, TASK_ID, OPERATION, CHANGED_AT, BEFORE_VERSION, AFTER_VERSION,
                 BEFORE_JSON, AFTER_JSON, SOURCE, INPUT_METHOD, BASIS, REASON, CONFIRMATION, FORMAT_VERSION)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), taskId, operation, Instant.now(clock).toString(),
                beforeVersion, afterVersion, json.writeValueAsString(before), json.writeValueAsString(after),
                source, inputMethod, null, null, confirmation, 1);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize task history", exception);
        }
    }

    @Configuration
    static class ClockConfiguration {
        @Bean("taskChangeClock")
        Clock taskChangeClock() {
            return Clock.systemUTC();
        }
    }
}
