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
import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectRepository;
import project_manager.service.ProjectService;
import project_manager.service.PortfolioOverviewService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_overview;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(PortfolioOverviewIntegrationTest.FixedTime.class)
@Transactional
class PortfolioOverviewIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary
        PortfolioOverviewService fixedOverview(ProjectService projects, ProjectRepository repository) {
            return new PortfolioOverviewService(projects, repository,
                Clock.fixed(Instant.parse("2026-09-18T21:30:00Z"), ZoneOffset.UTC));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository repository;
    @Autowired PortfolioOverviewService overview;
    @Autowired JdbcTemplate jdbc;

    @Test void yellowAndLowProgressDoNotCreateAttention() {
        project("yellow", "active", "yellow", "2026-10-20", "Q3", 100, 0);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.attentionCount()).isZero();
        assertThat(result.attentionProjects()).isEmpty();
    }

    @Test void completedPastDateIsNotOverdue() {
        project("done", "done", "green", "2020-01-01", "Q3", 100, 100);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.overdueCount()).isZero();
        assertThat(result.attentionCount()).isZero();
    }

    @Test void completedRedIsDataContradictionNotExecutionRisk() {
        project("done-red", "done", "red", "2020-01-01", "Q3", 100, 100);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.overdueCount()).isZero();
        assertThat(result.attentionProjects()).isEmpty();
        assertThat(result.dataQualityProjects()).hasSize(1);
        assertThat(result.dataQualityProjects().getFirst().dataQualityIssues())
            .contains("Противоречие данных: проект завершён, но состояние красное");
    }

    @Test void usesMoscowDateAndInclusiveCalendarBoundaries() throws Exception {
        project("yesterday", "active", "green", "2026-09-18", "Q3", 0, 0);
        project("today", "active", "green", "2026-09-19", "Q3", 0, 0);
        project("day14", "planned", "green", "2026-10-03", "Q3", 0, 0);
        project("day15", "paused", "green", "2026-10-04", "Q3", 0, 0);
        mvc.perform(get("/api/portfolio/overview")).andExpect(status().isOk())
            .andExpect(jsonPath("$.calculationDate").value("2026-09-19"))
            .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"))
            .andExpect(jsonPath("$.overdueCount").value(1))
            .andExpect(jsonPath("$.dueSoonCount").value(2))
            .andExpect(jsonPath("$.milestones[0].id").value("today"))
            .andExpect(jsonPath("$.milestones[1].id").value("day14"));
    }

    @Test void attentionUnionKeepsBothReasonsAndStableOrder() {
        project("red", "active", "red", "2026-10-20", "Q3", 0, 10);
        project("both", "active", "red", "2026-09-17", "Q3", 0, 10);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.attentionCount()).isEqualTo(2);
        assertThat(result.attentionProjects().getFirst().id()).isEqualTo("both");
        assertThat(result.attentionProjects().getFirst().attentionReasons()).hasSize(2);
    }

    @Test void filtersIntersectAndOptionsSurviveEmptySelection() throws Exception {
        project("a", "active", "green", "2026-10-20", "Q3", 100, 20);
        project("b", "done", "red", "2026-10-20", "Q4", 200, 100);
        mvc.perform(get("/api/portfolio/overview").param("quarter", "Q3").param("status", "done"))
            .andExpect(jsonPath("$.projectCount").value(0))
            .andExpect(jsonPath("$.totalProjectCount").value(2))
            .andExpect(jsonPath("$.quarters", hasItem("Q4")));
        mvc.perform(get("/api/portfolio/overview").param("status", "active").param("health", "green"))
            .andExpect(jsonPath("$.projectCount").value(1))
            .andExpect(jsonPath("$.totalBudget").value(100));
        mvc.perform(get("/api/projects").param("status", "done"))
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value("b"));
        mvc.perform(get("/api/projects")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void aggregatesBudgetWithoutIntegerOverflowAndRoundsProgress() {
        project("a", "active", "green", "2026-10-20", "Q3", 2_000_000_000, 20);
        project("b", "planned", "green", "2026-10-20", "Q4", 2_000_000_000, 21);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.totalBudget()).isEqualTo(4_000_000_000L);
        assertThat(result.averageProgress()).isEqualTo(21);
        assertThat(result.statusCounts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    }

    @Test void unknownAndInvalidValuesStayVisibleWithoutRepair() {
        project("bad", "custom", "purple", "2026-02-30", "Q3", -1, 120);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.totalBudget()).isNull();
        assertThat(result.averageProgress()).isNull();
        assertThat(result.projects().getFirst().daysUntilDeadline()).isNull();
        assertThat(result.statusCounts()).containsEntry("unknown", 1);
        assertThat(result.dataQualityProjects().getFirst().dataQualityIssues()).hasSize(5);
        assertThat(repository.findById("bad").orElseThrow().getDeadline()).isEqualTo("2026-02-30");
    }

    @Test void emptySelectionHasUnknownAveragesAndNoFictionalBudget() {
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.projectCount()).isZero();
        assertThat(result.totalProjectCount()).isZero();
        assertThat(result.averageProgress()).isNull();
        assertThat(result.totalBudget()).isNull();
    }

    @Test void zeroIsAValidKnownBudgetAndProgress() {
        project("zero", "active", "green", "2026-10-20", "Q3", 0, 0);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.totalBudget()).isZero();
        assertThat(result.averageProgress()).isZero();
        assertThat(result.budgetCoverage()).isEqualTo(1);
    }

    @Test void nullMetricsAreUnknownWhileStoredZeroRemainsReal() {
        project("missing", "active", "green", "2026-10-20", "Q3", 10, 10);
        var missing = repository.findById("missing").orElseThrow();
        missing.setBudget(null);
        missing.setProgress(null);
        repository.saveAndFlush(missing);
        project("zero", "active", "green", "2026-10-20", "Q3", 0, 0);

        var result = overview.getOverview("all", "all", "all");
        assertThat(result.totalBudget()).isZero();
        assertThat(result.averageProgress()).isZero();
        assertThat(result.budgetCoverage()).isEqualTo(1);
        assertThat(result.progressCoverage()).isEqualTo(1);
        assertThat(result.projects()).filteredOn(item -> item.id().equals("missing"))
            .allSatisfy(item -> {
                assertThat(item.budget()).isNull();
                assertThat(item.progress()).isNull();
            });
    }

    @Test void readAndFiltersPreserveAllStoredProjectFields() throws Exception {
        project("raw", "done", "red", "not-a-date", "Q4", 123, 77);
        repository.flush();
        var before = jdbc.queryForList("select * from projects order by id");
        mvc.perform(get("/api/portfolio/overview")).andExpect(status().isOk());
        mvc.perform(get("/api/portfolio/overview").param("status", "done")).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select * from projects order by id")).isEqualTo(before);
    }

    @Test void unknownDateDoesNotHideRedAttentionAndPartialCoverageIsExplicit() {
        project("red", "active", "red", "", "Q3", -1, -1);
        project("known", "active", "green", "2026-10-20", "Q3", 150, 50);
        var result = overview.getOverview("all", "all", "all");
        assertThat(result.attentionProjects()).hasSize(1);
        assertThat(result.totalBudget()).isEqualTo(150);
        assertThat(result.budgetCoverage()).isEqualTo(1);
        assertThat(result.progressCoverage()).isEqualTo(1);
    }

    private void project(String id, String status, String health, String deadline, String quarter, int budget, int progress) {
        ProjectEntity p = new ProjectEntity();
        p.setId(id); p.setName(id); p.setOwner("QA"); p.setStatus(status); p.setHealth(health);
        p.setDeadline(deadline); p.setQuarter(quarter); p.setBudget(budget); p.setProgress(progress);
        p.setDeliveryModel("kanban"); p.setMilestone("Milestone"); p.setRisk("Risk");
        p.setDependency("Dependency"); p.setKpiName("KPI"); p.setKpiTarget("Target"); p.setSummary("Summary");
        repository.saveAndFlush(p);
    }
}
