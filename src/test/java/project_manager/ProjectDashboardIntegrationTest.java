package project_manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.*;
import project_manager.repository.*;
import project_manager.service.ProjectAssessmentService;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_project_dashboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(ProjectDashboardIntegrationTest.FixedTime.class)
@Transactional
class ProjectDashboardIntegrationTest {
    @TestConfiguration static class FixedTime {
        @Bean @Primary ProjectAssessmentService dashboardClock() {
            return new ProjectAssessmentService(Clock.fixed(Instant.parse("2026-11-10T21:30:00Z"), ZoneOffset.UTC));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMilestoneRepository milestones;
    @SpyBean BoardCardRepository tasks;
    @Autowired JdbcTemplate jdbc;

    private ProjectEntity project() { return project("p"); }
    private ProjectEntity project(String id) {
        var p = new ProjectEntity(); p.setId(id); p.setName("Project"); p.setOwner("Owner");
        p.setStatus("active"); p.setHealth("yellow"); p.setDeliveryModel("kanban"); p.setQuarter("Q4");
        p.setDeadline("2026-12-31"); p.setMilestone("Legacy text is not a real milestone");
        p.setRisk("Risk"); p.setDependency("Dependency"); p.setKpiName("KPI"); p.setKpiTarget("Target");
        p.setSummary("Summary"); return projects.saveAndFlush(p);
    }
    private void task(String id, String column, String due, Boolean blocked) {
        task(id, "p", column, due, "medium", blocked);
    }
    private void task(String id, String projectId, String column, String due, String priority, Boolean blocked) {
        var t = new BoardCardEntity(); t.setId(id); t.setProjectId(projectId); t.setColumnKey(column);
        t.setPosition(0); t.setTitle(id); t.setDescription("Description"); t.setOwner("Owner");
        t.setDueDate(due); t.setPriority(priority); t.setLabels(""); t.setEstimate(1); t.setBlocked(blocked);
        tasks.saveAndFlush(t);
    }
    private void milestone(ProjectEntity p, String id, String date, boolean done) {
        var m = new ProjectMilestoneEntity(); m.setId(id); m.setProject(p); m.setName(id);
        m.setPlannedDate(LocalDate.parse(date)); m.setCompleted(done);
        m.setCompletedDate(done ? LocalDate.parse(date) : null); m.setPosition(0); milestones.saveAndFlush(m);
    }
    @Test void oneSnapshotCountsOverlapsUnknownsAndTrueMilestonesWithoutWriting() throws Exception {
        var p = project();
        task("done", "done", "2026-11-10", true); task("done-no-date", "done", "", false);
        task("late", "ready", "2026-11-10", true); task("today", "ready", "2026-11-11", false);
        task("future", "ready", "2026-11-25", null);
        task("retro", "retrospective", "2026-11-10", true); task("release", "release", "2026-11-10", false);
        task("unknown", "custom", "invalid", false); task("missing", "ready", "", false);
        task("invalid", "ready", "2026-02-30", false);
        milestone(p,"late-m","2026-11-10",false); milestone(p,"today-m","2026-11-11",false);
        milestone(p,"limit-m","2026-11-25",false); milestone(p,"outside-m","2026-11-26",false);
        milestone(p,"done-m","2026-11-09",true);
        var beforeProjects = jdbc.queryForList("select * from projects order by id");
        var beforeTasks = jdbc.queryForList("select * from board_cards order by id");
        var beforeMilestones = jdbc.queryForList("select * from project_milestones order by id");
        var result = mvc.perform(get("/api/projects/p/dashboard").param("model","waterfall"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("kanban"))
            .andExpect(jsonPath("$.calculationDate").value("2026-11-11"))
            .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"))
            .andExpect(jsonPath("$.tasks.total").value(10)).andExpect(jsonPath("$.tasks.done").value(2))
            .andExpect(jsonPath("$.tasks.blocked").value(3)).andExpect(jsonPath("$.tasks.overdue").value(1))
            .andExpect(jsonPath("$.tasks.uncertain").value(5)).andExpect(jsonPath("$.tasks.unknownDates").value(3))
            .andExpect(jsonPath("$.tasks.unmapped").value(3)).andExpect(jsonPath("$.tasks.unknownBlocked").value(1))
            .andExpect(jsonPath("$.milestones.overdue[0].id").value("late-m"))
            .andExpect(jsonPath("$.milestones.dueSoon.length()").value(2))
            .andExpect(jsonPath("$.milestones.dueSoon[0].id").value("today-m"))
            .andExpect(jsonPath("$.milestones.dueSoon[1].id").value("limit-m"))
            .andReturn();
        var data=json.readTree(result.getResponse().getContentAsString());
        long sum=0; for(var c:data.path("tasks").path("columns")) sum+=c.path("count").asLong();
        for(var c:data.path("tasks").path("unassigned")) sum+=c.path("count").asLong();
        assertThat(sum).isEqualTo(10);
        assertThat(data.path("tasks").path("overdueTasks").get(0).path("id").asText()).isEqualTo("late");
        assertThat(data.path("generatedAt").asText()).isNotBlank();
        assertThat(jdbc.queryForList("select * from projects order by id")).isEqualTo(beforeProjects);
        assertThat(jdbc.queryForList("select * from board_cards order by id")).isEqualTo(beforeTasks);
        assertThat(jdbc.queryForList("select * from project_milestones order by id")).isEqualTo(beforeMilestones);
    }
    @Test void onlyDoneCompletesWhileReleaseIsAnOpenWaterfallStage() throws Exception {
        var p=project(); p.setDeliveryModel(" Waterfall "); projects.flush();
        task("done","done","2026-11-10",false); task("release","release","2026-11-10",false);
        task("retro","retrospective","2026-11-10",false);
        mvc.perform(get("/api/projects/p/dashboard")).andExpect(status().isOk())
            .andExpect(jsonPath("$.model").value("waterfall"))
            .andExpect(jsonPath("$.tasks.done").value(1)).andExpect(jsonPath("$.tasks.overdue").value(1))
            .andExpect(jsonPath("$.tasks.uncertain").value(1));
    }
    @Test void emptyProjectHasKnownZerosAndUnknownProgressWhileMissingProjectIs404() throws Exception {
        var p=project(); p.setDeliveryModel(null); projects.flush();
        mvc.perform(get("/api/projects/p/dashboard")).andExpect(status().isOk())
            .andExpect(jsonPath("$.model").value("kanban"))
            .andExpect(jsonPath("$.tasks.total").value(0)).andExpect(jsonPath("$.tasks.overdue").value(0))
            .andExpect(jsonPath("$.project.progress").isEmpty())
            .andExpect(jsonPath("$.milestones.dueSoon").isEmpty());
        mvc.perform(get("/api/projects/missing/dashboard")).andExpect(status().isNotFound());
    }
    @Test void readFailureIsAnErrorNotSuccessfulEmptyData() throws Exception {
        project();
        doThrow(new DataAccessResourceFailureException("Test read failure")).when(tasks)
            .findByProjectIdOrderByPositionAsc("p");
        try { mvc.perform(get("/api/projects/p/dashboard")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("DATABASE_ERROR")); }
        finally { reset(tasks); }
    }
    @Test void dash03IsExactProjectOnlyFourStatesAndReadOnlyWithMoscowDate() throws Exception {
        var selected = project(); selected.setDeliveryModel("scrum");
        var other = project("other"); other.setDeliveryModel("waterfall"); projects.flush();
        task("late", "p", "backlog", "2026-11-10", "critical", false);
        task("today-high", "p", "ready", "2026-11-11", "high", false);
        task("today-critical", "p", "in-progress", "2026-11-11", "critical", false);
        task("undated", "p", "ready", "", "critical", false);
        task("unknown-priority", "p", "backlog", "2026-11-12", "unknown", false);
        task("complete", "p", "done", "2026-11-10", "high", false);
        task("historic", "p", "release", "2026-11-10", "critical", false);
        task("standalone", null, "ready", "2026-11-10", "critical", false);
        task("foreign", "other", "ready", "2026-11-10", "critical", false);
        var before = jdbc.queryForList("select * from board_cards order by id");
        var result = mvc.perform(get("/api/projects/p/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dash03.backlog").value(2))
            .andExpect(jsonPath("$.dash03.ready").value(2))
            .andExpect(jsonPath("$.dash03.inProgress").value(1))
            .andExpect(jsonPath("$.dash03.done").value(1))
            .andExpect(jsonPath("$.dash03.open").value(5))
            .andExpect(jsonPath("$.dash03.highPriority").value(4))
            .andExpect(jsonPath("$.dash03.overdue").value(1))
            .andExpect(jsonPath("$.dash03.unknownDates").value(1))
            .andExpect(jsonPath("$.dash03.unknownPriorities").value(1))
            .andExpect(jsonPath("$.dash03.overdueTasks[0].id").value("late"))
            .andExpect(jsonPath("$.dash03.upcomingTasks[0].id").value("today-critical"))
            .andExpect(jsonPath("$.dash03.upcomingTasks[1].id").value("today-high"))
            .andExpect(jsonPath("$.dash03.criticalUndatedTasks[0].id").value("undated"))
            .andExpect(jsonPath("$.tasks.total").value(7))
            .andReturn();
        var data = json.readTree(result.getResponse().getContentAsString());
        assertThat(data.path("dash03").toString()).doesNotContain("historic", "standalone", "foreign");
        assertThat(jdbc.queryForList("select * from board_cards order by id")).isEqualTo(before);
        mvc.perform(get("/api/projects/other/dashboard"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("waterfall"))
            .andExpect(jsonPath("$.dash03.ready").value(1))
            .andExpect(jsonPath("$.dash03.overdueTasks[0].id").value("foreign"));
    }
}
