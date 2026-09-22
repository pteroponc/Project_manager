package project_manager.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import project_manager.domain.ProjectEntity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared, read-only business assessment used by portfolio and project APIs. */
@Service
public class ProjectAssessmentService {
    public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Set<String> STATUSES = Set.of("active", "planned", "paused", "done");
    private static final Set<String> HEALTH = Set.of("green", "yellow", "red");
    private final Clock clock;

    @Autowired
    public ProjectAssessmentService() {
        this(Clock.system(ZONE));
    }

    public ProjectAssessmentService(Clock clock) {
        this.clock = clock.withZone(ZONE);
    }

    public LocalDate calculationDate() {
        return LocalDate.now(clock);
    }

    public Assessment assess(ProjectEntity project, LocalDate calculationDate) {
        String status = normalized(project.getStatus());
        String health = normalized(project.getHealth());
        boolean done = "done".equals(status);
        List<String> issues = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        if (!STATUSES.contains(status)) issues.add("Неизвестный статус: " + project.getStatus());
        if (!HEALTH.contains(health)) issues.add("Неизвестное состояние: " + project.getHealth());
        if (project.getBudget() != null && project.getBudget() < 0) {
            issues.add("Некорректный указанный бюджет");
        }
        if (project.getProgress() != null && (project.getProgress() < 0 || project.getProgress() > 100)) {
            issues.add("Некорректный указанный прогресс");
        }

        LocalDate deadline = parseDate(project.getDeadline());
        Long days = deadline == null ? null : deadline.toEpochDay() - calculationDate.toEpochDay();
        if ("red".equals(health)) {
            if (done) issues.add("Противоречие данных: проект завершён, но состояние красное");
            else reasons.add("Красное состояние");
        }
        boolean overdue = false;
        if (days == null) {
            issues.add("Не удалось определить срок проекта: " + project.getDeadline());
        } else if (!done && days < 0) {
            overdue = true;
            reasons.add("Срок проекта просрочен на " + (-days) + " дн.");
        }
        if (project.getMilestone() == null || project.getMilestone().isBlank()) {
            issues.add("Контрольная точка не указана");
        }

        return new Assessment(status, health, deadline, days, overdue, !reasons.isEmpty(),
            List.copyOf(reasons), List.copyOf(issues));
    }

    public boolean isKnownStatus(String value) {
        return STATUSES.contains(normalized(value));
    }

    public boolean isKnownHealth(String value) {
        return HEALTH.contains(normalized(value));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record Assessment(
        String normalizedStatus,
        String normalizedHealth,
        LocalDate deadline,
        Long daysUntilDeadline,
        boolean overdue,
        boolean requiresAttention,
        List<String> attentionReasons,
        List<String> dataQualityIssues
    ) { }
}
