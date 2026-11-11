package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.BoardCardEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.web.dto.ProjectDashboardResponse;
import project_manager.web.dto.ProjectDashboardResponse.*;
import project_manager.web.dto.ProjectCardResponse.MilestoneResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ProjectDashboardService {
    private final ProjectService projects;
    private final ProjectCardService cards;
    private final BoardService boards;
    private final BoardCardRepository tasks;

    public ProjectDashboardService(ProjectService projects, ProjectCardService cards,
                                   BoardService boards, BoardCardRepository tasks) {
        this.projects = projects; this.cards = cards; this.boards = boards; this.tasks = tasks;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectDashboardResponse getDashboard(String id) {
        var entity = projects.requireProject(id);
        var project = cards.getCard(id);
        var today = LocalDate.parse(project.calculationDate());
        String model = boards.normalizeDeliveryModel(entity.getDeliveryModel());
        var templates = boards.columnsFor(model, entity);
        Set<String> mapped = new HashSet<>();
        templates.forEach(column -> mapped.add(column.key()));
        Map<String, Long> counts = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<Task> blockedTasks = new ArrayList<>(), overdueTasks = new ArrayList<>();
        long done = 0, uncertain = 0, unknownDates = 0, unmapped = 0, unknownBlocked = 0;
        for (var task : tasks.findByProjectIdOrderByPositionAsc(id)) {
            if (!seen.add(task.getId())) continue;
            String column = task.getColumnKey();
            counts.merge(column, 1L, Long::sum);
            boolean completed = "done".equals(column);
            boolean assigned = mapped.contains(column);
            var due = parseDate(task.getDueDate());
            if (completed) done++;
            if (!assigned) unmapped++;
            if (!completed && due == null) unknownDates++;
            if (!completed && (!assigned || due == null)) uncertain++;
            if (task.getBlocked() == null) unknownBlocked++;
            if (Boolean.TRUE.equals(task.getBlocked())) blockedTasks.add(toTask(task));
            if (!completed && assigned && due != null && due.isBefore(today)) overdueTasks.add(toTask(task));
        }
        blockedTasks.sort(Comparator.comparing(Task::title, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::id));
        overdueTasks.sort(Comparator.comparing(Task::dueDate).thenComparing(Task::id));
        var columns = templates.stream().map(column ->
            new ColumnCount(column.key(), column.title(), counts.getOrDefault(column.key(), 0L))).toList();
        var unassigned = counts.keySet().stream().filter(key -> !mapped.contains(key))
            .sorted(Comparator.nullsFirst(Comparator.naturalOrder()))
            .map(key -> new ColumnCount(key, key, counts.get(key))).toList();
        var milestoneOrder = Comparator.comparing(MilestoneResponse::plannedDate)
            .thenComparingInt(MilestoneResponse::position).thenComparing(MilestoneResponse::id);
        var overdueMilestones = project.milestones().stream().filter(m -> !m.completed()
            && m.plannedDate() != null && m.plannedDate().isBefore(today)).sorted(milestoneOrder).toList();
        var dueSoon = project.milestones().stream().filter(m -> !m.completed() && m.plannedDate() != null
            && !m.plannedDate().isBefore(today) && !m.plannedDate().isAfter(today.plusDays(14)))
            .sorted(milestoneOrder).toList();
        long unknownMilestones = project.milestones().stream()
            .filter(m -> !m.completed() && m.plannedDate() == null).count();
        return new ProjectDashboardResponse(project.calculationDate(), project.timeZone(), Instant.now(), model,
            project, new Tasks(seen.size(), done, blockedTasks.size(), overdueTasks.size(), uncertain,
            unknownDates, unmapped, unknownBlocked, columns, unassigned, blockedTasks, overdueTasks),
            new Milestones(overdueMilestones, dueSoon, unknownMilestones));
    }

    private Task toTask(BoardCardEntity task) {
        return new Task(task.getId(), task.getTitle(), task.getColumnKey(), task.getOwner(),
            task.getDueDate(), task.getBlocked(), task.getVersion());
    }

    private LocalDate parseDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }
}
