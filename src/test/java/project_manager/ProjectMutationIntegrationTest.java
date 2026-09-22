package project_manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.repository.ProjectRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_b2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ProjectMutationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean ProjectRepository projects;
    @Autowired ProjectMilestoneRepository milestones;
    @Autowired JdbcTemplate jdbc;

    @Test
    void projectPatchChangesOnlyGivenFieldAndIncrementsVersionOnce() throws Exception {
        String id = project("green");
        var before = storedProject(id);
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"Updated\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.budget").doesNotExist());
        var after = storedProject(id);
        assertThat(after).containsEntry("BUDGET", before.get("BUDGET"))
            .containsEntry("PROGRESS", before.get("PROGRESS"))
            .containsEntry("START_DATE", before.get("START_DATE"));
        assertThat(after.get("OWNER")).isEqualTo(before.get("OWNER"));
        assertThat(after.get("NAME")).isEqualTo("Updated");
    }

    @Test
    void explicitNullClearsProgressAndStartDateButZeroRemainsReal() throws Exception {
        String id = project("green");
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"progress\":null,\"startDate\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.progress").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.startDate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.version").value(1));
        assertThat(storedProject(id).get("BUDGET")).isEqualTo(123456);
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"progress\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.progress").value(0))
            .andExpect(jsonPath("$.version").value(2));
        assertThat(storedProject(id).get("PROGRESS")).isEqualTo(0);
    }

    @Test
    void noOpPatchStillAdvancesVersionExactlyOnce() throws Exception {
        String id = project("green");
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"Original\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(projects.findById(id).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void forbiddenUnknownAndEmptyPatchLeaveProjectUnchanged() throws Exception {
        String id = project("green");
        var before = storedProject(id);
        for (String body : List.of(
            "{\"expectedVersion\":0,\"budget\":0}",
            "{\"expectedVersion\":0,\"heath\":\"red\"}")) {
            mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(storedProject(id)).isEqualTo(before);
    }

    @Test
    void invalidProjectFieldsReturnFieldErrorsWithoutChangingData() throws Exception {
        String id = project("green");
        var before = storedProject(id);
        Map<String, String> invalid = Map.of(
            "name", "null", "progress", "101", "deadline", "\"2026-02-30\"",
            "status", "\"unknown\"", "health", "\"purple\"",
            "deliveryModel", "\"unknown\"");
        for (var entry : invalid.entrySet()) {
            mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":0,\"" + entry.getKey() + "\":" + entry.getValue() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors." + entry.getKey()).exists());
        }
        assertThat(storedProject(id)).isEqualTo(before);
    }

    @Test
    void sequentialClientsWithSameVersionGetConflictAndNoPartialChange() throws Exception {
        String id = project("green");
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"First\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        var afterFirst = storedProject(id);
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"owner\":\"Second\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROJECT_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(1));
        assertThat(storedProject(id)).isEqualTo(afterFirst);
    }

    @Test
    void oldUnknownValuesArePreservedWhenAnotherFieldChanges() throws Exception {
        String id = project("purple");
        mvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"Renamed\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.health").value("purple"));
        assertThat(storedProject(id).get("HEALTH")).isEqualTo("purple");
    }

    @Test
    void milestoneCreatePatchDeleteAdvanceVersionAndKeepStableOrder() throws Exception {
        String id = project("green");
        JsonNode second = postMilestone(id, 0, "Second", 2);
        String secondId = second.path("milestones").get(0).path("id").asText();
        assertThat(second.path("version").asLong()).isEqualTo(1);
        JsonNode first = postMilestone(id, 1, "First", 1);
        String firstId = first.path("milestones").get(0).path("id").asText();
        assertThat(first.path("version").asLong()).isEqualTo(2);
        assertThat(first.path("milestones").get(0).path("name").asText()).isEqualTo("First");
        assertThat(first.path("milestones").get(1).path("name").asText()).isEqualTo("Second");

        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", id, secondId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2,\"completed\":true,\"completedDate\":\"2026-10-19\",\"position\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.milestoneCount").value(2));
        JsonNode ordered = card(id);
        assertThat(ordered.path("milestones").get(0).path("id").asText())
            .isEqualTo(firstId.compareTo(secondId) < 0 ? firstId : secondId);

        mvc.perform(delete("/api/projects/{projectId}/milestones/{milestoneId}", id, firstId)
                .param("expectedVersion", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(4))
            .andExpect(jsonPath("$.milestoneCount").value(1))
            .andExpect(jsonPath("$.milestones[0].id").value(secondId));
    }

    @Test
    void invalidMilestoneCombinationAndNegativePositionCauseNoWrites() throws Exception {
        String id = project("green");
        for (String fields : List.of(
            "\"name\":\"M\",\"plannedDate\":\"2026-10-20\",\"position\":-1",
            "\"name\":\"M\",\"plannedDate\":\"2026-10-20\",\"position\":0,\"completed\":true",
            "\"name\":\"M\",\"plannedDate\":\"2026-10-20\",\"position\":0,\"completedDate\":\"2026-10-19\"",
            "\"name\":\" \",\"plannedDate\":\"2026-10-20\",\"position\":0")) {
            mvc.perform(post("/api/projects/{id}/milestones", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":0," + fields + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
        }
        assertThat(projects.findById(id).orElseThrow().getVersion()).isZero();
        assertThat(milestones.countByProject_Id(id)).isZero();
    }

    @Test
    void foreignMilestoneAndStaleVersionDoNotChangeEitherProject() throws Exception {
        String owner = project("green");
        String other = project("yellow");
        String milestoneId = postMilestone(owner, 0, "Owned", 0).path("milestones").get(0).path("id").asText();
        var beforeProjects = jdbc.queryForList("select * from projects where id in (?,?) order by id", owner, other);
        var beforeMilestones = jdbc.queryForList("select * from project_milestones where id=?", milestoneId);

        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", other, milestoneId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"Stolen\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MILESTONE_NOT_FOUND"));
        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", owner, milestoneId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"Stale\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROJECT_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(1));
        mvc.perform(delete("/api/projects/{projectId}/milestones/{milestoneId}", owner, milestoneId)
                .param("expectedVersion", "0"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(1));
        assertThat(jdbc.queryForList("select * from projects where id in (?,?) order by id", owner, other))
            .isEqualTo(beforeProjects);
        assertThat(jdbc.queryForList("select * from project_milestones where id=?", milestoneId))
            .isEqualTo(beforeMilestones);
    }

    @Test
    void staleMilestoneCreateReturnsCurrentVersionWithoutCreatingRow() throws Exception {
        String id = project("green");
        postMilestone(id, 0, "First", 0);
        var beforeProject = storedProject(id);
        var beforeMilestones = jdbc.queryForList("select * from project_milestones where project_id=?", id);
        mvc.perform(post("/api/projects/{id}/milestones", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"Stale\",\"plannedDate\":\"2026-10-20\",\"position\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROJECT_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(1));
        assertThat(storedProject(id)).isEqualTo(beforeProject);
        assertThat(jdbc.queryForList("select * from project_milestones where project_id=?", id))
            .isEqualTo(beforeMilestones);
    }

    @Test
    void projectDisappearingDuringVersionBumpReturns404WithoutPartialWrites() throws Exception {
        String id = project("green");
        var beforeProject = storedProject(id);
        var beforeMilestones = jdbc.queryForList("select * from project_milestones where project_id=?", id);
        doReturn(0).when(projects).incrementVersionIfCurrent(id, 0);
        doReturn(null).when(projects).currentVersion(id);

        mvc.perform(post("/api/projects/{id}/milestones", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"Raced\",\"plannedDate\":\"2026-10-20\",\"position\":0}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
            .andExpect(jsonPath("$.currentVersion").doesNotExist());

        assertThat(storedProject(id)).isEqualTo(beforeProject);
        assertThat(jdbc.queryForList("select * from project_milestones where project_id=?", id))
            .isEqualTo(beforeMilestones);
    }

    @Test
    void unknownMilestoneFieldsAndMissingVersionAreRejected() throws Exception {
        String id = project("green");
        mvc.perform(post("/api/projects/{id}/milestones", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"M\",\"plannedDate\":\"2026-10-20\",\"position\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());
        mvc.perform(post("/api/projects/{id}/milestones", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"M\",\"plannedDate\":\"2026-10-20\",\"position\":0,\"budget\":2}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(milestones.countByProject_Id(id)).isZero();
    }

    @Test
    void milestonePatchChecksMergedCompletedStateAndAllowsExplicitDateClear() throws Exception {
        String id = project("green");
        String milestoneId = postMilestone(id, 0, "M", 0)
            .path("milestones").get(0).path("id").asText();
        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", id, milestoneId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"completed\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.completedDate").exists());
        assertThat(projects.findById(id).orElseThrow().getVersion()).isEqualTo(1);

        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", id, milestoneId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"completed\":true,\"completedDate\":\"2026-10-19\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.milestones[0].completed").value(true));
        var beforeInvalid = jdbc.queryForList("select * from project_milestones where id=?", milestoneId);
        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", id, milestoneId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2,\"position\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.position").exists());
        assertThat(jdbc.queryForList("select * from project_milestones where id=?", milestoneId))
            .isEqualTo(beforeInvalid);

        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", id, milestoneId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2,\"completed\":false,\"completedDate\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.milestones[0].completed").value(false))
            .andExpect(jsonPath("$.milestones[0].completedDate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void missingProjectMilestoneAndDeleteVersionReturnStructuredErrors() throws Exception {
        String id = project("green");
        mvc.perform(patch("/api/projects/{id}", "missing-project")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"X\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(patch("/api/projects/{projectId}/milestones/{milestoneId}", id, "missing-milestone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"name\":\"X\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MILESTONE_NOT_FOUND"));
        mvc.perform(delete("/api/projects/{projectId}/milestones/{milestoneId}", id, "missing-milestone"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());
    }

    @Test
    void projectWithMilestoneStillCannotBeDeletedAndReadsDoNotWrite() throws Exception {
        String id = project("green");
        postMilestone(id, 0, "M", 0);
        var beforeProject = storedProject(id);
        var beforeMilestones = jdbc.queryForList("select * from project_milestones where project_id=?", id);
        mvc.perform(get("/api/projects/{id}/card", id)).andExpect(status().isOk());
        mvc.perform(get("/api/projects/registry")).andExpect(status().isOk());
        mvc.perform(get("/api/projects/{id}/deletion-impact", id)).andExpect(status().isOk());
        assertThat(storedProject(id)).isEqualTo(beforeProject);
        assertThat(jdbc.queryForList("select * from project_milestones where project_id=?", id))
            .isEqualTo(beforeMilestones);
        mvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isConflict());
        assertThat(storedProject(id)).isEqualTo(beforeProject);
        assertThat(jdbc.queryForList("select * from project_milestones where project_id=?", id))
            .isEqualTo(beforeMilestones);
    }

    private String project(String health) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID().toString());
        project.setName("Original");
        project.setOwner("Owner");
        project.setStatus("active");
        project.setHealth(health);
        project.setDeliveryModel("kanban");
        project.setBudget(123456);
        project.setProgress(40);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setQuarter("Q4 2026");
        project.setDeadline("2026-10-20");
        project.setMilestone("Legacy milestone");
        project.setRisk("Risk");
        project.setDependency("Dependency");
        project.setKpiName("KPI");
        project.setKpiTarget("Target");
        project.setSummary("Summary");
        return projects.saveAndFlush(project).getId();
    }

    private Map<String, Object> storedProject(String id) {
        return jdbc.queryForMap("select * from projects where id=?", id);
    }

    private JsonNode card(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/projects/{id}/card", id))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode postMilestone(String id, long version, String name, int position) throws Exception {
        String body = "{\"expectedVersion\":" + version + ",\"name\":\"" + name
            + "\",\"plannedDate\":\"2026-10-20\",\"position\":" + position + "}";
        return json.readTree(mvc.perform(post("/api/projects/{id}/milestones", id)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
