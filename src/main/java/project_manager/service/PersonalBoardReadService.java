package project_manager.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.BoardCardEntity;
import project_manager.domain.ProjectEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.dto.PersonalBoardResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PersonalBoardReadService {
    private static final List<String> KANBAN = List.of("backlog", "ready", "in-progress", "done");
    private static final List<String> TITLES = List.of("Бэклог", "К выполнению", "В работе", "Готово");
    private static final Comparator<BoardCardEntity> DISPLAY_ORDER = Comparator
        .comparing(BoardCardEntity::getProjectId, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparingInt(BoardCardEntity::getPosition)
        .thenComparing(BoardCardEntity::getId);

    private final BoardCardRepository cards;
    private final ProjectRepository projects;

    public PersonalBoardReadService(BoardCardRepository cards, ProjectRepository projects) {
        this.cards = cards;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public PersonalBoardResponse read() {
        Map<String, String> projectNames = projects.findAll().stream()
            .collect(Collectors.toMap(ProjectEntity::getId, ProjectEntity::getName));
        List<BoardCardEntity> ordered = cards.findAll().stream().sorted(DISPLAY_ORDER).toList();
        List<PersonalBoardResponse.Column> columns = java.util.stream.IntStream.range(0, KANBAN.size())
            .mapToObj(index -> new PersonalBoardResponse.Column(KANBAN.get(index), TITLES.get(index),
                ordered.stream().filter(card -> KANBAN.get(index).equals(card.getColumnKey()))
                    .map(card -> task(card, projectNames)).toList()))
            .toList();
        List<PersonalBoardResponse.Task> legacy = ordered.stream()
            .filter(card -> !KANBAN.contains(card.getColumnKey()))
            .map(card -> task(card, projectNames)).toList();
        return new PersonalBoardResponse(columns, legacy);
    }

    private static PersonalBoardResponse.Task task(BoardCardEntity card, Map<String, String> projectNames) {
        return new PersonalBoardResponse.Task(card.getId(), card.getProjectId(),
            card.getProjectId() == null ? null : projectNames.get(card.getProjectId()),
            card.getColumnKey(), card.getPosition(), card.getTitle(), card.getDescription(),
            card.getOwner(), card.getDueDate(), card.getPriority(), card.getLabels(),
            card.getEstimate() == null ? 0 : card.getEstimate(), Boolean.TRUE.equals(card.getBlocked()),
            card.getVersion());
    }
}
