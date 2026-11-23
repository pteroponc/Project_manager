package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_my_day;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class MyDayIntegrationTest {
    private static final Instant BEFORE_MOSCOW_MIDNIGHT = Instant.parse("2026-11-23T20:59:59Z");
    private static final AtomicReference<Instant> NOW = new AtomicReference<>();
    @TestBean(name = "myDayClock", methodName = "testClock", enforceOverride = true) Clock clock;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    static Clock testClock() { return movingClock(ZoneId.of("America/Los_Angeles")); }

    private static Clock movingClock(ZoneId zone) {
        return new Clock() {
            @Override public ZoneId getZone() { return zone; }
            @Override public Clock withZone(ZoneId other) { return movingClock(other); }
            @Override public Instant instant() { return NOW.get(); }
        };
    }

    @BeforeEach
    void setDate() { NOW.set(BEFORE_MOSCOW_MIDNIGHT); }

    @Test
    void groupsByMoscowDateAndSortsByDateThenPriorityThenIdWithoutMutations() throws Exception {
        String active = project("active");
        String paused = project("paused");
        String done = project("done");
        card("older", active, "ready", "2026-11-21", "low");
        card("late", null, "ready", "2026-11-22", "critical");
        card("today-b", null, "ready", "2026-11-23", "high");
        card("today-low", active, "ready", "2026-11-23", "low");
        card("today-a", active, "in-progress", "2026-11-23", "high");
        card("tomorrow", active, "backlog", "2026-11-24", "low");
        card("next-year", active, "backlog", "2027-01-05", "critical");
        card("no-date", null, "ready", "", "high");
        card("no-date-low", null, "ready", "", "low");
        card("invalid", active, "ready", "2026-02-30", "medium");
        card("legacy", active, "in-sprint", "2026-11-22", "critical");
        card("done-late", active, "done", "2026-11-22", "critical");
        card("done-today", active, "done", "2026-11-23", "critical");
        card("paused-project", paused, "ready", "2026-11-22", "critical");
        card("done-project", done, "ready", "2026-11-22", "critical");
        var before = jdbc.queryForList("select * from board_cards order by id");
        var history = jdbc.queryForList("select * from task_change_entries");

        JsonNode day = read();
        assertThat(day.path("calculationDate").asText()).isEqualTo("2026-11-23");
        assertThat(day.path("timeZone").asText()).isEqualTo("Europe/Moscow");
        assertThat(day.path("hasUnknownStates").asBoolean()).isTrue();
        List<String> actual = new ArrayList<>();
        day.path("tasks").forEach(task -> actual.add(task.path("id").asText() + ":" + task.path("reason").asText()));
        assertThat(actual).containsExactly("older:OVERDUE", "late:OVERDUE", "today-a:TODAY", "today-b:TODAY",
            "today-low:TODAY", "tomorrow:UPCOMING", "next-year:UPCOMING", "no-date:PRIORITY_WITHOUT_DATE", "invalid:INVALID_DATE");
        assertThat(task(day, "no-date").path("highPriority").asBoolean()).isTrue();
        assertThat(task(day, "today-low").path("priority").asText()).isEqualTo("low");
        assertThat(task(day, "invalid").path("dueDate").isNull()).isTrue();
        assertThat(task(day, "invalid").path("recordedDueDate").asText()).isEqualTo("2026-02-30");
        assertThat(task(day, "late").path("projectId").isNull()).isTrue();
        assertThat(task(day, "older").path("projectId").asText()).isEqualTo(active);

        NOW.set(Instant.parse("2026-11-23T21:00:00Z"));
        JsonNode next = read();
        assertThat(next.path("calculationDate").asText()).isEqualTo("2026-11-24");
        assertThat(task(next, "tomorrow").path("reason").asText()).isEqualTo("TODAY");
        assertThat(task(next, "today-a").path("reason").asText()).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForList("select * from board_cards order by id")).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from task_change_entries")).isEqualTo(history);
    }

    @Test
    void prioritizesUnfinishedPastMilestoneWithoutMutatingSource() throws Exception {
        assertThat(read().path("tasks").isEmpty()).isTrue();
        String active = project("active");
        milestone("past", active, "2026-11-22", false);
        milestone("today", active, "2026-11-23", false);
        milestone("future", active, "2026-11-24", false);
        milestone("completed", active, "2026-11-19", true);
        milestone("paused", project("paused"), "2026-11-20", false);
        milestone("closed", project("done"), "2026-11-21", false);
        var beforeMilestones = jdbc.queryForList("select * from project_milestones order by id");
        var beforeProjects = jdbc.queryForList("select * from projects order by id");
        var beforeCards = jdbc.queryForList("select * from board_cards");
        var beforeHistory = jdbc.queryForList("select * from task_change_entries");
        JsonNode selected = read().path("milestone");
        assertThat(selected.path("id").asText()).isEqualTo("past");
        assertThat(selected.path("plannedDateRelation").asText()).isEqualTo("PAST");
        assertThat(jdbc.queryForList("select * from project_milestones order by id")).isEqualTo(beforeMilestones);
        assertThat(jdbc.queryForList("select * from projects order by id")).isEqualTo(beforeProjects);
        assertThat(jdbc.queryForList("select * from board_cards")).isEqualTo(beforeCards);
        assertThat(jdbc.queryForList("select * from task_change_entries")).isEqualTo(beforeHistory);
    }

    @Test
    void choosesTheFirstDatedMilestoneAndDistinguishesFutureFromToday() throws Exception {
        String active = project("active");
        milestone("later", active, "2026-11-26", false);
        milestone("nearest-b", active, "2026-11-24", false);
        milestone("nearest-a", active, "2026-11-24", false);
        JsonNode future = read().path("milestone");
        assertThat(future.path("id").asText()).isEqualTo("nearest-a");
        assertThat(future.path("plannedDateRelation").asText()).isEqualTo("FUTURE");
        NOW.set(Instant.parse("2026-11-23T21:00:00Z"));
        assertThat(read().path("milestone").path("plannedDateRelation").asText()).isEqualTo("TODAY");
    }

    @Test
    void showsNoMilestoneWhenOnlyLegacyTextOrCompletedRecordsExist() throws Exception {
        String active = project("active");
        assertThat(read().path("milestone").isNull()).isTrue();
        milestone("completed", active, "2026-11-22", true);
        milestone("contradictory", active, "2026-11-23", false);
        jdbc.update("update project_milestones set completed_date = DATE '2026-11-23' where id='contradictory'");
        assertThat(read().path("milestone").isNull()).isTrue();
    }

    @Test
    void rereadsSameBoardCardThroughDateEditAndCompletionWithHistory() throws Exception {
        String tomorrow = """
            {"title":"Shared task","columnKey":"backlog","priority":"high","dueDate":"2026-11-24"}
            """;
        String id = json.readTree(mvc.perform(post("/api/board/personal/tasks").contentType(MediaType.APPLICATION_JSON)
            .content(tomorrow)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        assertThat(task(read(), id).path("reason").asText()).isEqualTo("UPCOMING");
        mvc.perform(get("/api/board/personal")).andExpect(jsonPath("$.columns[0].tasks[0].id").value(id));
        update(id, "backlog", 0);
        assertThat(task(read(), id).path("reason").asText()).isEqualTo("TODAY");
        update(id, "done", 1);
        assertThat(task(read(), id).isMissingNode()).isTrue();
        assertThat(jdbc.queryForList("select version from board_cards where id=?", Long.class, id)).containsExactly(2L);
        assertThat(jdbc.queryForList("select operation from task_change_entries where task_id=? order by sequence",
            String.class, id)).containsExactly("CREATE", "UPDATE", "COMPLETE");
    }

    private JsonNode read() throws Exception {
        return json.readTree(mvc.perform(get("/api/my-day").header("Time-Zone", "Pacific/Honolulu"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode task(JsonNode data, String id) {
        for (JsonNode task : data.path("tasks")) if (id.equals(task.path("id").asText())) return task;
        return json.missingNode();
    }

    private void update(String id, String state, int version) throws Exception {
        mvc.perform(put("/api/board/personal/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
            {"title":"Shared task","columnKey":"%s","priority":"high","dueDate":"2026-11-23","expectedVersion":%d}
            """.formatted(state, version))).andExpect(status().isOk());
    }

    private String project(String status) throws Exception {
        String id = json.readTree(mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"Review project","owner":"Review","status":"%s","health":"green",
             "deliveryModel":"scrum","quarter":"Q4","deadline":"2026-11-23","milestone":"Unscheduled legacy text",
             "risk":"Risk","dependency":"Dependency","kpiName":"KPI","kpiTarget":"Target","summary":"Review"}
            """.formatted(status))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
        entityManager.flush();
        return id;
    }

    private void card(String id, String project, String state, String due, String priority) {
        jdbc.update("""
            insert into board_cards (id, project_id, column_key, position, title, description, owner, due_date,
                                     priority, labels, estimate, blocked, version)
            values (?, ?, ?, 0, ?, '', '', ?, ?, '', 0, false, 0)
            """, id, project, state, id, due, priority);
    }

    private void milestone(String id, String project, String date, boolean completed) {
        jdbc.update("insert into project_milestones (id, project_id, name, planned_date, completed, position) values (?, ?, ?, ?, ?, 0)",
            id, project, id, java.time.LocalDate.parse(date), completed);
    }
}
