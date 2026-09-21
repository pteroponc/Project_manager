package project_manager.safety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;
import java.util.Map;
import java.util.Set;

/** Reject unsafe demo/smoke configuration before DataSource initialization. */
public class IsolatedDatabaseGuard implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("demo", "smoke"))) return;
        if (environment.acceptsProfiles(Profiles.of("demo"))
                && !java.util.Arrays.asList(environment.getActiveProfiles()).contains("demo")) {
            throw new IllegalStateException("Demo must be explicitly activated, not configured as a default profile");
        }
        requireMemoryDatabase(environment, "jdbc:h2:mem:pm(demo|smoke|test)_[A-Za-z0-9_-]+(;MODE=PostgreSQL)?(;DB_CLOSE_DELAY=-1)?");
    }

    public static void requireMemoryDatabase(ConfigurableEnvironment environment, String pattern) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.matches(pattern)) throw new IllegalStateException("Isolated mode requires an approved in-memory H2 URL");
        String stableUrl = url.contains(";DB_CLOSE_DELAY=-1") ? url : url + ";DB_CLOSE_DELAY=-1";
        for (String key : new String[]{"spring.datasource.jndi-name", "spring.datasource.type",
                "spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-jndi", "spring.datasource.hikari.connection-init-sql"}) {
            if (environment.containsProperty(key)) throw new IllegalStateException("Unsafe datasource override: " + key);
        }
        Set<String> forbidden = Set.of("springdatasourcejndiname", "springdatasourcetype",
            "springdatasourcehikarijdbcurl", "springdatasourcehikaridatasourceclassname",
            "springdatasourcehikaridatasourcejndi", "springdatasourcehikariconnectioninitsql");
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    String normalized = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if (forbidden.contains(normalized)
                            || normalized.startsWith("springdatasourcehikaridatasourceproperties")
                            || normalized.startsWith("springjpapropertieshibernateconnection")
                            || normalized.startsWith("springjpapropertiesjakartapersistencejdbc")) {
                        throw new IllegalStateException("Unsafe connection properties in isolated mode");
                    }
                }
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource("isolatedDatabase", Map.of(
            "spring.datasource.url", stableUrl,
            "spring.datasource.hikari.jdbc-url", stableUrl,
            "spring.datasource.driver-class-name", "org.h2.Driver",
            "spring.datasource.hikari.driver-class-name", "org.h2.Driver",
            "spring.sql.init.mode", "never")));
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE - 1; }
}
