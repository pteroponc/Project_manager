package project_manager.service;

import org.junit.jupiter.api.Test;
import project_manager.domain.ProjectEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectAssessmentServiceTest {
    private final ProjectAssessmentService service = new ProjectAssessmentService(
        Clock.fixed(Instant.parse("2026-09-20T21:00:00Z"), ZoneOffset.UTC));
    private final LocalDate today = LocalDate.of(2026, 9, 21);

    @Test
    void yellowLowProgressAndNearDeadlineDoNotRequireAttention() {
        var result = service.assess(project("active", "yellow", "2026-09-22", 1), today);

        assertThat(result.requiresAttention()).isFalse();
        assertThat(result.overdue()).isFalse();
        assertThat(result.attentionReasons()).isEmpty();
    }

    @Test
    void redAndOverdueReasonsAreCombinedForUnfinishedProject() {
        var result = service.assess(project("active", "red", "2026-09-20", 50), today);

        assertThat(result.requiresAttention()).isTrue();
        assertThat(result.overdue()).isTrue();
        assertThat(result.daysUntilDeadline()).isEqualTo(-1);
        assertThat(result.attentionReasons()).containsExactly(
            "Красное состояние", "Срок проекта просрочен на 1 дн.");
    }

    @Test
    void completedProjectIsNeverOverdueAndRedIsADataContradiction() {
        var result = service.assess(project("done", "red", "2020-01-01", 100), today);

        assertThat(result.requiresAttention()).isFalse();
        assertThat(result.overdue()).isFalse();
        assertThat(result.attentionReasons()).isEmpty();
        assertThat(result.dataQualityIssues())
            .contains("Противоречие данных: проект завершён, но состояние красное");
    }

    @Test
    void unknownValuesAndInvalidStoredNumbersRemainVisibleAsQualityIssues() {
        var project = project("custom", "purple", "2026-02-30", 120);
        project.setBudget(-1);
        project.setMilestone("");

        var result = service.assess(project, today);

        assertThat(result.daysUntilDeadline()).isNull();
        assertThat(result.requiresAttention()).isFalse();
        assertThat(result.dataQualityIssues()).containsExactly(
            "Неизвестный статус: custom",
            "Неизвестное состояние: purple",
            "Некорректный указанный бюджет",
            "Некорректный указанный прогресс",
            "Не удалось определить срок проекта: 2026-02-30",
            "Контрольная точка не указана");
    }

    @Test
    void calculationDateUsesMoscowTimeZone() {
        assertThat(service.calculationDate()).isEqualTo(today);
    }

    private ProjectEntity project(String status, String health, String deadline, int progress) {
        ProjectEntity project = new ProjectEntity();
        project.setId("p");
        project.setName("Project");
        project.setOwner("Owner");
        project.setStatus(status);
        project.setHealth(health);
        project.setDeliveryModel("kanban");
        project.setBudget(100);
        project.setProgress(progress);
        project.setQuarter("Q3 2026");
        project.setDeadline(deadline);
        project.setMilestone("Milestone");
        project.setRisk("Risk");
        project.setDependency("Dependency");
        project.setKpiName("KPI");
        project.setKpiTarget("Target");
        project.setSummary("Summary");
        return project;
    }
}
