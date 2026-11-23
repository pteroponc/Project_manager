package project_manager.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.BoardCardEntity;
import project_manager.domain.ProjectEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectMilestoneRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.dto.MyDayResponse;
import project_manager.web.dto.MyDayResponse.Milestone;
import project_manager.web.dto.MyDayResponse.PlannedDateRelation;
import project_manager.web.dto.MyDayResponse.Reason;
import project_manager.web.dto.MyDayResponse.Task;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MyDayService {
    private static final List<String> OPEN_STATES = List.of("backlog", "ready", "in-progress");
    private static final List<String> OPERATIVE_PROJECTS = List.of("active", "planned");
    private static final Comparator<Task> TASK_ORDER = Comparator.comparing(Task::reason)
        .thenComparing(Task::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparingInt(task -> priorityRank(task.priority()))
        .thenComparing(Task::id);

    private final BoardCardRepository cards;
    private final ProjectRepository projects;
    private final ProjectMilestoneRepository milestones;
    private final Clock clock;

    public MyDayService(BoardCardRepository cards, ProjectRepository projects, ProjectMilestoneRepository milestones,
                        @Qualifier("myDayClock") Clock clock) {
        this.cards = cards;
        this.projects = projects;
        this.milestones = milestones;
        this.clock = clock.withZone(ProjectAssessmentService.ZONE);
    }

    @Transactional(readOnly = true)
    public MyDayResponse read() {
        LocalDate today = LocalDate.now(clock);
        Map<String, ProjectEntity> byId = projects.findAll().stream()
            .collect(Collectors.toMap(ProjectEntity::getId, Function.identity()));
        List<BoardCardEntity> available = cards.findAll().stream()
            .filter(card -> canInclude(card, byId)).toList();
        boolean unknownStates = available.stream().anyMatch(card ->
            !"done".equals(card.getColumnKey()) && !OPEN_STATES.contains(card.getColumnKey()));
        List<Task> tasks = available.stream().filter(card -> OPEN_STATES.contains(card.getColumnKey()))
            .map(card -> summarize(card, byId, today)).filter(Objects::nonNull).sorted(TASK_ORDER).toList();
        Milestone milestone = milestones
            .findUnfinishedByPlannedDate(OPERATIVE_PROJECTS, PageRequest.of(0, 1))
            .stream().findFirst().map(item -> new Milestone(item.getId(), item.getName(), item.getProjectId(),
                item.getProject().getName(), item.getPlannedDate(), plannedDateRelation(item.getPlannedDate(), today)))
            .orElse(null);
        return new MyDayResponse(today, ProjectAssessmentService.ZONE.getId(), tasks, milestone, unknownStates);
    }

    private static PlannedDateRelation plannedDateRelation(LocalDate planned, LocalDate today) {
        if (planned.isBefore(today)) return PlannedDateRelation.PAST;
        return planned.equals(today) ? PlannedDateRelation.TODAY : PlannedDateRelation.FUTURE;
    }

    private static boolean canInclude(BoardCardEntity card, Map<String, ProjectEntity> projects) {
        if (card.getProjectId() == null) return true;
        ProjectEntity project = projects.get(card.getProjectId());
        return project != null && OPERATIVE_PROJECTS.contains(project.getStatus());
    }

    private static Task summarize(BoardCardEntity card, Map<String, ProjectEntity> projects, LocalDate today) {
        String recorded = card.getDueDate();
        LocalDate date = parseDate(recorded);
        boolean high = priorityRank(card.getPriority()) <= 1;
        Reason reason;
        if (date != null) {
            reason = date.isBefore(today) ? Reason.OVERDUE : date.equals(today) ? Reason.TODAY : Reason.UPCOMING;
        } else if (recorded != null && !recorded.isBlank()) {
            reason = Reason.INVALID_DATE;
        } else if (high) {
            reason = Reason.PRIORITY_WITHOUT_DATE;
        } else {
            return null;
        }
        ProjectEntity project = projects.get(card.getProjectId());
        return new Task(card.getId(), card.getTitle(), card.getProjectId(), project == null ? null : project.getName(),
            card.getColumnKey(), card.getPriority(), high, recorded, date, reason);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static int priorityRank(String value) {
        return switch (value == null ? "" : value) {
            case "critical" -> 0;
            case "high" -> 1;
            case "medium" -> 2;
            case "low" -> 3;
            default -> 4;
        };
    }

    @Configuration
    static class ClockConfiguration {
        @Bean("myDayClock")
        Clock myDayClock() {
            return Clock.system(ProjectAssessmentService.ZONE);
        }
    }
}
