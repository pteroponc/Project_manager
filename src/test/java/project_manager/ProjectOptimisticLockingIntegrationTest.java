package project_manager;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.repository.ProjectRepository;
import project_manager.service.ProjectCardService;
import project_manager.service.ProjectDeletionService;
import project_manager.service.ProjectRegistryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:pmtest_optimistic;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ProjectOptimisticLockingIntegrationTest {
    @Autowired ProjectRepository projects;
    @Autowired ProjectMilestoneRepository milestones;
    @Autowired ProjectRegistryService registry;
    @Autowired ProjectCardService cards;
    @Autowired ProjectDeletionService deletion;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void readsDoNotIncrementVersionAndStaleUpdateCannotOverwriteNewerChange() {
        ProjectEntity stored = project("locking");
        projects.saveAndFlush(stored);
        Long initialVersion = projects.findById("locking").orElseThrow().getVersion();
        assertThat(initialVersion).isZero();

        projects.findById("locking").orElseThrow();
        registry.getRegistry("", "all", "all", "all", "all", "name", "asc");
        cards.getCard("locking");
        deletion.getImpact("locking");
        milestones.findByProject_IdOrderByPositionAscIdAsc("locking");
        assertThat(projects.findById("locking").orElseThrow().getVersion()).isEqualTo(initialVersion);

        var first = entityManagerFactory.createEntityManager();
        var stale = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            stale.getTransaction().begin();
            ProjectEntity firstCopy = first.find(ProjectEntity.class, "locking");
            ProjectEntity staleCopy = stale.find(ProjectEntity.class, "locking");
            firstCopy.setName("First committed change");
            first.getTransaction().commit();

            staleCopy.setName("Stale overwrite");
            assertThatThrownBy(() -> stale.getTransaction().commit())
                .hasRootCauseInstanceOf(org.hibernate.StaleObjectStateException.class);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            first.close();
            stale.close();
        }

        ProjectEntity actual = projects.findById("locking").orElseThrow();
        assertThat(actual.getName()).isEqualTo("First committed change");
        assertThat(actual.getVersion()).isEqualTo(initialVersion + 1);
    }

    private ProjectEntity project(String id) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setName("Original");
        project.setOwner("Owner");
        project.setStatus("active");
        project.setHealth("green");
        project.setDeliveryModel("kanban");
        project.setBudget(null);
        project.setProgress(null);
        project.setQuarter("Q3 2026");
        project.setDeadline("2027-01-01");
        project.setMilestone("Legacy milestone");
        project.setRisk("Risk");
        project.setDependency("Dependency");
        project.setKpiName("KPI");
        project.setKpiTarget("Target");
        project.setSummary("Summary");
        return project;
    }
}
