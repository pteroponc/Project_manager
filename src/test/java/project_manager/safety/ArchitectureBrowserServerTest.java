package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.util.ContentCachingResponseWrapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import project_manager.ProjectManagerApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static project_manager.safety.ArchitectureSchemaMigrationIntegrationTest.*;

/** Opt-in real HTTP server, compiled only on the test classpath. Never opens a file database. */
class ArchitectureBrowserServerTest {
    @TestConfiguration
    static class HttpEvidence {
        @Bean
        FilterRegistrationBean<Filter> reviewEvidence() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter((request, response, chain) -> {
                var req = (HttpServletRequest) request;
                var cached = new ContentCachingResponseWrapper((HttpServletResponse) response);
                try {
                    chain.doFilter(request, cached);
                    if (req.getRequestURI().startsWith("/api/")) {
                        String line = new ObjectMapper().writeValueAsString(java.util.Map.of(
                            "method", req.getMethod(), "path", req.getRequestURI(),
                            "query", req.getQueryString() == null ? "" : req.getQueryString(),
                            "status", cached.getStatus(), "response", new String(cached.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)));
                        synchronized (HttpEvidence.class) {
                            Files.writeString(Path.of("tmp", "PM-003C.4-http.jsonl"), line + "\n",
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        }
                    }
                } finally { cached.copyBodyToResponse(); }
            });
            return registration;
        }
    }
    static void checkBeforeConnection(String url) {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", url);
        new TestDatabaseGuard().postProcessEnvironment(environment, new SpringApplication(ProjectManagerApplication.class));
    }

    @Test
    void rejectsUnsafeTargetsBeforeJdbc() {
        for (String url : new String[]{"jdbc:h2:file:./forbidden", "jdbc:h2:mem:other", "jdbc:h2:mem:pmtest_c4;INIT=bad"})
            assertThatThrownBy(() -> checkBeforeConnection(url)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> checkBeforeConnection("jdbc:h2:mem:pmtest_c4;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"))
            .doesNotThrowAnyException();
    }

    @Test
    @EnabledIfSystemProperty(named = "pm.c4.browser", matches = "true")
    void serveRealApplicationForBrowserReview() throws Exception {
        String url = "jdbc:h2:mem:pmtest_c4_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        checkBeforeConnection(url); // Mandatory guard BEFORE DriverManager or schema preparation.
        Path stop = Path.of("tmp", "PM-003C.4-stop.txt");
        Files.createDirectories(stop.getParent());
        Files.deleteIfExists(stop);
        Files.deleteIfExists(Path.of("tmp", "PM-003C.4-http.jsonl"));
        try (var connection = setup(url, true)) {
            script(connection, "V004__architecture_security.sql");
            script(connection, "V005__architecture_passport_instance.sql");
            jdbc(connection).update("update projects set name='C4 Fictional Atlas', owner='Review Owner', quarter='Q1' where id='zero'");
        }
        try (var context = new SpringApplicationBuilder(ProjectManagerApplication.class, HttpEvidence.class).run(
            "--spring.profiles.active=smoke", "--spring.datasource.url=" + url,
            "--spring.jpa.hibernate.ddl-auto=validate", "--spring.sql.init.mode=never",
            "--spring.h2.console.enabled=false", "--server.address=127.0.0.1", "--server.port=4184",
            "--logging.level.root=WARN")) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo(url);
            System.out.println("C4_REAL_SERVER_READY http://127.0.0.1:4184 (test memory only; V002-V005; validate)");
            long deadline = System.nanoTime() + Duration.ofMinutes(45).toNanos();
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(250);
            assertThat(Files.exists(stop)).as("Stop explicitly using tmp/PM-003C.4-stop.txt within 45 minutes").isTrue();
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", "")) {
                connection.createStatement().execute("SHUTDOWN");
            }
            Files.deleteIfExists(stop);
        }
        System.out.println("C4_REAL_SERVER_STOPPED");
    }
}
