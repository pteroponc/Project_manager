package project_manager.safety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Runs before a DataSource or Hibernate can open a connection. */
public class TestDatabaseGuard implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Revalidate original configuration if the demo/smoke guard has already run.
        environment.getPropertySources().remove("isolatedDatabase");
        IsolatedDatabaseGuard.requireMemoryDatabase(environment,
            "jdbc:h2:mem:pmtest_[A-Za-z0-9_-]+(;MODE=PostgreSQL)?(;DB_CLOSE_DELAY=-1)?");
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}