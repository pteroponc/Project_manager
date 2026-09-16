package project_manager.safety;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import project_manager.domain.*;
import project_manager.repository.*;
import project_manager.service.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataSafetyRegressionTest {
    @Test
    void startupDoesNotCreateProjects() throws Exception {
        var repository = mock(ProjectRepository.class);
        runStartupCallbacks(new ProjectService(repository));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void startupDoesNotOverwriteProjectWithDemoName() throws Exception {
        var repository = mock(ProjectRepository.class);
        var project = project();
        when(repository.count()).thenReturn(1L);
        when(repository.findByName(anyString())).thenReturn(Optional.of(project));
        runStartupCallbacks(new ProjectService(repository));
        assertThat(project.getOwner()).isEqualTo("User owner");
        verify(repository, never()).save(any());
    }

    @Test
    void startupDoesNotCreateTrains() throws Exception {
        var repository = mock(ReleaseTrainRepository.class);
        runStartupCallbacks(new ReleaseTrainService(repository));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void startupDoesNotOverwriteTrainWithDemoName() throws Exception {
        var repository = mock(ReleaseTrainRepository.class);
        var train = new ReleaseTrainEntity();
        train.setId("user-train");
        train.setName("RT-2026-Q3 Platform");
        train.setRisk("User risk");
        when(repository.count()).thenReturn(1L);
        when(repository.findByName(anyString())).thenReturn(Optional.of(train));
        runStartupCallbacks(new ReleaseTrainService(repository));
        assertThat(train.getRisk()).isEqualTo("User risk");
        verify(repository, never()).save(any());
    }

    @Test
    void readingAllBoardModelsDoesNotCreateOrRestoreCards() {
        var repository = mock(BoardCardRepository.class);
        var projects = mock(ProjectService.class);
        when(projects.requireProject("p")).thenReturn(project());
        when(repository.findByProjectIdOrderByPositionAsc("p")).thenReturn(List.of());
        var service = new BoardService(projects, repository);
        for (String model : List.of("kanban", "scrum", "waterfall", "kanban")) {
            assertThat(service.getBoard("p", model).columns()).allSatisfy(c -> assertThat(c.cards()).isEmpty());
        }
        verify(repository, never()).saveAll(any());
        verify(repository, never()).save(any());
    }

    @Test
    void readingPreservesEditedTemplateCardAndUnknownColumn() {
        var repository = mock(BoardCardRepository.class);
        var projects = mock(ProjectService.class);
        when(projects.requireProject("p")).thenReturn(project());
        var card = new BoardCardEntity();
        card.setId("p-scope");
        card.setProjectId("p");
        card.setColumnKey("legacy-column");
        card.setPosition(42);
        card.setTitle("User title");
        card.setDescription("User description");
        card.setOwner("User owner");
        card.setDueDate("2031-01-01");
        card.setPriority("high");
        card.setLabels("user-label");
        when(repository.findByProjectIdOrderByPositionAsc("p")).thenReturn(List.of(card));
        var service = new BoardService(projects, repository);
        for (String model : List.of("kanban", "scrum", "waterfall")) service.getBoard("p", model);
        assertThat(card.getColumnKey()).isEqualTo("legacy-column");
        assertThat(card.getPosition()).isEqualTo(42);
        assertThat(card.getTitle()).isEqualTo("User title");
        assertThat(card.getDescription()).isEqualTo("User description");
        assertThat(card.getOwner()).isEqualTo("User owner");
        assertThat(card.getDueDate()).isEqualTo("2031-01-01");
        assertThat(card.getPriority()).isEqualTo("high");
        assertThat(card.getLabels()).isEqualTo("user-label");
        assertThat(card.getEstimate()).isNull();
        assertThat(card.getBlocked()).isNull();
        verify(repository, never()).saveAll(any());
    }

    private static void runStartupCallbacks(Object service) throws Exception {
        for (var method : service.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                method.setAccessible(true);
                method.invoke(service);
            }
        }
    }

    private static ProjectEntity project() {
        var project = new ProjectEntity();
        project.setId("p");
        project.setName("Digital Commerce Platform");
        project.setOwner("User owner");
        project.setDeliveryModel("kanban");
        project.setDeadline("2031-01-01");
        return project;
    }
}
