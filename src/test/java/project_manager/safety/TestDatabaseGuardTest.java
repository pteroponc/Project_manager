package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class TestDatabaseGuardTest {
    @Test
    void rejectsFileAndInjectedUrlsBeforeConnection() {
        for (String url : new String[]{"jdbc:h2:file:./target/forbidden-test-target;IFEXISTS=TRUE", "jdbc:h2:tcp://localhost/test",
                "jdbc:h2:mem:pmtest_safe;INIT=RUNSCRIPT FROM 'unsafe.sql'", ""}) {
            assertThatThrownBy(() -> new TestDatabaseGuard().postProcessEnvironment(
                new MockEnvironment().withProperty("spring.datasource.url", url), null))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsAlternatePoolUrl() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:pmtest_safe")
            .withProperty("spring.datasource.hikari.jdbc-url", "jdbc:h2:file:./target/forbidden-test-target;IFEXISTS=TRUE");
        assertThatThrownBy(() -> new TestDatabaseGuard().postProcessEnvironment(env, null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsRelaxedPoolOverridesAndJpaConnectionProperties() {
        for (String key : new String[]{"spring.datasource.hikari.jdbcUrl", "SPRING_DATASOURCE_HIKARI_JDBC_URL",
                "spring.datasource.hikari.data-source-properties.url", "spring.jpa.properties.hibernate.connection.url"}) {
            var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:pmtest_safe")
                .withProperty(key, "jdbc:h2:file:./target/forbidden-test-target;IFEXISTS=TRUE");
            assertThatThrownBy(() -> new TestDatabaseGuard().postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void acceptsOnlyIsolatedMemoryDatabase() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:pmtest_safe");
        new TestDatabaseGuard().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.hikari.jdbc-url")).isEqualTo("jdbc:h2:mem:pmtest_safe");
    }

    @Test
    void demoAndSmokeRejectFileDatabaseAndAcceptMemory() {
        for (String profile : new String[]{"demo", "smoke"}) {
            var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:file:./target/forbidden-test-target;IFEXISTS=TRUE");
            env.setActiveProfiles(profile);
            assertThatThrownBy(() -> new IsolatedDatabaseGuard().postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class);
            env.setProperty("spring.datasource.url", "jdbc:h2:mem:pm" + profile + "_safe");
            new IsolatedDatabaseGuard().postProcessEnvironment(env, null);
        }
    }

    @Test
    void demoCannotBeEnabledAsDefaultProfile() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:pmdemo_safe");
        env.setDefaultProfiles("demo");
        assertThatThrownBy(() -> new IsolatedDatabaseGuard().postProcessEnvironment(env, null))
            .hasMessageContaining("explicitly activated");
    }
}
