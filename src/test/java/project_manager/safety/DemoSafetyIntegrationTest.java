package project_manager.safety;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import project_manager.repository.*;
import project_manager.service.*;
import project_manager.web.dto.BoardCardRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("demo")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_demo;MODE=PostgreSQL")
class DemoSafetyIntegrationTest {
    @Autowired DemoDataInitializer initializer;
    @Autowired ProjectRepository projects;
    @Autowired ReleaseTrainRepository trains;
    @Autowired BoardCardRepository cards;
    @MockitoSpyBean BoardService boards;

    @Test
    void seedsOncePreservesEditsAndRollsBackFailedInitialization() {
        assertThat(projects.count()).isEqualTo(3);
        assertThat(trains.count()).isEqualTo(2);
        assertThat(cards.count()).isEqualTo(3);
        var project = projects.findAll().getFirst();
        project.setOwner("User changed demo"); projects.save(project);
        var removed = cards.findAll().getFirst(); cards.delete(removed);
        initializer.run(new DefaultApplicationArguments());
        assertThat(projects.findById(project.getId()).orElseThrow().getOwner()).isEqualTo("User changed demo");
        assertThat(cards.existsById(removed.getId())).isFalse();
        assertThat(projects.count()).isEqualTo(3);
        // Only synthetic data in this isolated memory database is removed for the rollback scenario.
        cards.deleteAll(); trains.deleteAll(); projects.deleteAll();
        doThrow(new IllegalStateException("simulated failure")).when(boards)
            .createCard(anyString(), any(BoardCardRequest.class), anyString());
        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
            .hasMessageContaining("simulated failure");
        assertThat(projects.count()).isZero();
        assertThat(trains.count()).isZero();
        assertThat(cards.count()).isZero();
    }
}
