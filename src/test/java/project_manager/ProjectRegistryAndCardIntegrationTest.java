package project_manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.BoardCardEntity;
import project_manager.domain.ProjectEntity;
import project_manager.domain.ProjectMilestoneEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.repository.ProjectRepository;
import project_manager.service.ProjectAssessmentService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_registry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(ProjectRegistryAndCardIntegrationTest.FixedTime.class)
@Transactional
class ProjectRegistryAndCardIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        ProjectAssessmentService fixedAssessment() {
            return new ProjectAssessmentService(
                Clock.fixed(Instant.parse("2026-09-20T21:30:00Z"), ZoneOffset.UTC));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired BoardCardRepository cards;
    @Autowired ProjectMilestoneRepository milestones;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registrySearchFiltersAndAttentionReusePm001Rules() throws Exception {
        project("red", "Alpha", "Launch platform", "active", "red", "2026-10-20");
        project("overdue", "Beta", "Legacy migration", "active", "green", "2026-09-20");
        project("yellow", "Gamma", "Launch support", "active", "yellow", "2026-09-22");
        project("done-red", "Delta", "Closed launch", "done", "red", "2020-01-01");

        mvc.perform(get("/api/projects/registry")
                .param("query", "launch")
                .param("status", "active")
                .param("attention", "only")
                .param("sort", "health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.calculationDate").value("2026-09-21"))
            .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"))
            .andExpect(jsonPath("$.totalCount").value(4))
            .andExpect(jsonPath("$.filteredCount").value(1))
            .andExpect(jsonPath("$.items[0].id").value("red"))
            .andExpect(jsonPath("$.items[0].budget").doesNotExist())
            .andExpect(jsonPath("$.items[0].assessment.requiresAttention").value(true))
            .andExpect(jsonPath("$.items[0].assessment.attentionReasons[0]").value("Красное состояние"))
            .andExpect(jsonPath("$.filterOptions.statuses[*]").value(org.hamcrest.Matchers.hasItem("done")));
    }

    @Test
    void registrySortsDeadlinesAndKeepsInvalidDateVisibleLast() throws Exception {
        project("later", "Later", "Summary", "active", "green", "2026-10-20");
        project("invalid", "Invalid", "Summary", "active", "green", "not-a-date");
        project("earlier", "Earlier", "Summary", "active", "green", "2026-09-25");

        mvc.perform(get("/api/projects/registry").param("sort", "deadline"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("earlier"))
            .andExpect(jsonPath("$.items[1].id").value("later"))
            .andExpect(jsonPath("$.items[2].id").value("invalid"))
            .andExpect(jsonPath("$.items[2].assessment.dataQualityIssues[0]")
                .value(containsString("Не удалось определить срок")));

        mvc.perform(get("/api/projects/registry").param("sort", "deadline").param("direction", "desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("later"))
            .andExpect(jsonPath("$.items[1].id").value("earlier"))
            .andExpect(jsonPath("$.items[2].id").value("invalid"));
    }

    @Test
    void healthSortKeepsUnknownValuesVisibleAndLast() throws Exception {
        project("green", "Green", "Summary", "active", "green", "2026-10-20");
        project("unknown", "Unknown", "Summary", "active", "purple", "2026-10-20");
        project("red", "Red", "Summary", "active", "red", "2026-10-20");
        project("yellow", "Yellow", "Summary", "active", "yellow", "2026-10-20");

        mvc.perform(get("/api/projects/registry").param("sort", "health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("red"))
            .andExpect(jsonPath("$.items[1].id").value("yellow"))
            .andExpect(jsonPath("$.items[2].id").value("green"))
            .andExpect(jsonPath("$.items[3].id").value("unknown"));
    }

    @Test
    void registryRejectsUnsupportedControlParameters() throws Exception {
        mvc.perform(get("/api/projects/registry").param("sort", "budget"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void cardReturnsStoredProjectAssessmentAndRealTaskCount() throws Exception {
        project("p", "Project", "Summary", "active", "green", "2026-09-20");
        ProjectEntity stored = projects.findById("p").orElseThrow();
        stored.setDeliveryModel("legacy-model");
        stored.setStartDate(java.time.LocalDate.of(2026, 1, 15));
        projects.saveAndFlush(stored);
        card("c1", "p");
        card("c2", "p");
        milestone("m-2", "p", 2, false);
        milestone("m-1b", "p", 1, false);
        milestone("m-1a", "p", 1, true);

        mvc.perform(get("/api/projects/p/card"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Project"))
            .andExpect(jsonPath("$.owner").value("Owner"))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.health").value("green"))
            .andExpect(jsonPath("$.deliveryModel").value("legacy-model"))
            .andExpect(jsonPath("$.progress").value(50))
            .andExpect(jsonPath("$.startDate").value("2026-01-15"))
            .andExpect(jsonPath("$.version").isNumber())
            .andExpect(jsonPath("$.assessment.overdue").value(true))
            .andExpect(jsonPath("$.assessment.daysUntilDeadline").value(-1))
            .andExpect(jsonPath("$.board.viewAvailable").value(true))
            .andExpect(jsonPath("$.board.taskCount").value(2))
            .andExpect(jsonPath("$.milestoneCount").value(3))
            .andExpect(jsonPath("$.milestones[0].id").value("m-1a"))
            .andExpect(jsonPath("$.milestones[1].id").value("m-1b"))
            .andExpect(jsonPath("$.milestones[2].id").value("m-2"))
            .andExpect(jsonPath("$.budget").doesNotExist())
            .andExpect(jsonPath("$.releaseCount").doesNotExist())
            .andExpect(jsonPath("$.documentCount").doesNotExist());
    }

    @Test
    void readApisDoNotModifyStoredRows() throws Exception {
        project("p", "Project", "Summary", "active", "green", "2026-10-20");
        card("c", "p");
        milestone("m", "p", 0, false);
        projects.flush();
        cards.flush();
        milestones.flush();
        var projectsBefore = jdbc.queryForList("select * from projects order by id");
        var cardsBefore = jdbc.queryForList("select * from board_cards order by id");
        var milestonesBefore = jdbc.queryForList("select * from project_milestones order by id");

        mvc.perform(get("/api/projects/registry")).andExpect(status().isOk());
        mvc.perform(get("/api/projects/p/card")).andExpect(status().isOk());
        mvc.perform(get("/api/projects/p/deletion-impact")).andExpect(status().isOk());

        assertThat(jdbc.queryForList("select * from projects order by id")).isEqualTo(projectsBefore);
        assertThat(jdbc.queryForList("select * from board_cards order by id")).isEqualTo(cardsBefore);
        assertThat(jdbc.queryForList("select * from project_milestones order by id")).isEqualTo(milestonesBefore);
    }

    @Test
    void deletionImpactBlocksProjectWithTasksWithoutChangingData() throws Exception {
        project("p", "Project", "Summary", "active", "green", "2026-10-20");
        card("c", "p");
        projects.flush();
        cards.flush();
        var projectsBefore = jdbc.queryForList("select * from projects order by id");
        var cardsBefore = jdbc.queryForList("select * from board_cards order by id");

        mvc.perform(get("/api/projects/p/deletion-impact"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Project"))
            .andExpect(jsonPath("$.taskCount").value(1))
            .andExpect(jsonPath("$.milestoneCount").value(0))
            .andExpect(jsonPath("$.deletionAllowed").value(false))
            .andExpect(jsonPath("$.blockers[0]").value("У проекта есть задачи: 1"));

        mvc.perform(delete("/api/projects/p"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROJECT_HAS_BOARD_CARDS"));
        assertThat(jdbc.queryForList("select * from projects order by id")).isEqualTo(projectsBefore);
        assertThat(jdbc.queryForList("select * from board_cards order by id")).isEqualTo(cardsBefore);
    }

    @Test
    void deletionIsBlockedByMilestonesWithoutPartialChanges() throws Exception {
        project("p", "Project", "Summary", "active", "green", "2026-10-20");
        milestone("m", "p", 0, false);
        projects.flush();
        milestones.flush();
        var projectsBefore = jdbc.queryForList("select * from projects order by id");
        var milestonesBefore = jdbc.queryForList("select * from project_milestones order by id");

        mvc.perform(get("/api/projects/p/deletion-impact"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskCount").value(0))
            .andExpect(jsonPath("$.milestoneCount").value(1))
            .andExpect(jsonPath("$.deletionAllowed").value(false))
            .andExpect(jsonPath("$.blockers[0]").value("У проекта есть контрольные точки: 1"));

        mvc.perform(delete("/api/projects/p"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROJECT_HAS_MILESTONES"));
        assertThat(jdbc.queryForList("select * from projects order by id")).isEqualTo(projectsBefore);
        assertThat(jdbc.queryForList("select * from project_milestones order by id")).isEqualTo(milestonesBefore);
    }

    @Test
    void projectWithoutTasksCanStillBeDeleted() throws Exception {
        project("p", "Project", "Summary", "active", "green", "2026-10-20");

        mvc.perform(get("/api/projects/p/deletion-impact"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskCount").value(0))
            .andExpect(jsonPath("$.milestoneCount").value(0))
            .andExpect(jsonPath("$.deletionAllowed").value(true));
        mvc.perform(delete("/api/projects/p")).andExpect(status().isNoContent());
        assertThat(projects.existsById("p")).isFalse();
    }

    @Test
    void missingCardAndImpactReturnNotFound() throws Exception {
        mvc.perform(get("/api/projects/missing/card")).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/missing/deletion-impact")).andExpect(status().isNotFound());
    }

    private void project(String id, String name, String summary, String status, String health, String deadline) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setName(name);
        project.setOwner("Owner");
        project.setStatus(status);
        project.setHealth(health);
        project.setDeliveryModel("kanban");
        project.setBudget(100);
        project.setProgress(50);
        project.setQuarter("Q3 2026");
        project.setDeadline(deadline);
        project.setMilestone("Milestone");
        project.setRisk("Risk");
        project.setDependency("Dependency");
        project.setKpiName("KPI");
        project.setKpiTarget("Target");
        project.setSummary(summary);
        projects.saveAndFlush(project);
    }

    private void card(String id, String projectId) {
        BoardCardEntity card = new BoardCardEntity();
        card.setId(id);
        card.setProjectId(projectId);
        card.setColumnKey("backlog");
        card.setPosition(0);
        card.setTitle("Task " + id);
        card.setDescription("Description");
        card.setOwner("Owner");
        card.setDueDate("2026-10-20");
        card.setPriority("medium");
        card.setLabels("");
        card.setEstimate(1);
        card.setBlocked(false);
        cards.saveAndFlush(card);
    }

    private void milestone(String id, String projectId, int position, boolean completed) {
        ProjectMilestoneEntity milestone = new ProjectMilestoneEntity();
        milestone.setId(id);
        milestone.setProject(projects.findById(projectId).orElseThrow());
        milestone.setName("Milestone " + id);
        milestone.setPlannedDate(java.time.LocalDate.of(2026, 10, 20));
        milestone.setCompleted(completed);
        milestone.setCompletedDate(completed ? java.time.LocalDate.of(2026, 10, 19) : null);
        milestone.setPosition(position);
        milestones.saveAndFlush(milestone);
    }
}
