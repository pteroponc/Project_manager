package project_manager.service;

import project_manager.domain.BoardCard;
import project_manager.domain.BoardCardEntity;
import project_manager.domain.BoardColumn;
import project_manager.domain.ProjectBoard;
import project_manager.domain.ProjectEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.web.dto.BoardCardRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BoardService {
    private final ProjectService projectService;
    private final BoardCardRepository boardCardRepository;

    public BoardService(ProjectService projectService, BoardCardRepository boardCardRepository) {
        this.projectService = projectService;
        this.boardCardRepository = boardCardRepository;
    }

    public ProjectBoard getBoard(String projectId) {
        return getBoard(projectId, null);
    }

    public ProjectBoard getBoard(String projectId, String boardModel) {
        ProjectEntity project = projectService.requireProject(projectId);
        String deliveryModel = normalizeDeliveryModel(
            boardModel == null || boardModel.isBlank() ? project.getDeliveryModel() : boardModel
        );
        List<BoardColumn> templateColumns = columnsFor(deliveryModel, project);
        List<BoardCardEntity> cards = boardCardRepository.findByProjectIdOrderByPositionAsc(projectId);
        return new ProjectBoard(
            project.getId(),
            project.getName(),
            deliveryModel,
            boardTitle(deliveryModel),
            mergeCards(templateColumns, cards),
            cards.stream().map(BoardCardEntity::getColumnKey).distinct()
                .filter(key -> templateColumns.stream().noneMatch(column -> column.key().equals(key)))
                .sorted()
                .map(key -> new BoardColumn(key, key, null, cards.stream()
                    .filter(card -> key.equals(card.getColumnKey()))
                    .sorted(Comparator.comparingInt(BoardCardEntity::getPosition))
                    .map(this::toCard).toList()))
                .toList(),
            cards.stream().collect(Collectors.toMap(BoardCardEntity::getId, BoardCardEntity::getPosition))
        );
    }

    public List<String> getAvailableDeliveryModels() {
        return List.of("kanban", "scrum", "waterfall");
    }

    public BoardCard createCard(String projectId, BoardCardRequest request) {
        return createCard(projectId, request, null);
    }

    public BoardCard createCard(String projectId, BoardCardRequest request, String boardModel) {
        ProjectEntity project = projectService.requireProject(projectId);
        List<BoardColumn> columns = columnsFor(project, boardModel);
        validateColumn(columns, request.columnKey());
        int nextPosition = boardCardRepository
            .findByProjectIdAndColumnKeyOrderByPositionAsc(projectId, request.columnKey())
            .stream()
            .mapToInt(BoardCardEntity::getPosition)
            .max()
            .orElse(-1) + 1;

        BoardCardEntity card = new BoardCardEntity();
        card.setId(projectId + "-task-" + UUID.randomUUID());
        card.setProjectId(projectId);
        card.setColumnKey(request.columnKey());
        card.setPosition(nextPosition);
        card.setTitle(request.title());
        card.setDescription(request.description());
        card.setOwner(request.owner());
        card.setDueDate(request.dueDate());
        card.setPriority(normalizePriority(request.priority()));
        card.setLabels(normalizeLabels(request.labels()));
        card.setEstimate(request.estimate());
        card.setBlocked(request.blocked());
        return toCard(boardCardRepository.save(card));
    }

    public BoardCard updateCard(String projectId, String cardId, BoardCardRequest request) {
        return updateCard(projectId, cardId, request, null);
    }

    public BoardCard updateCard(String projectId, String cardId, BoardCardRequest request, String boardModel) {
        ProjectEntity project = projectService.requireProject(projectId);
        List<BoardColumn> columns = columnsFor(project, boardModel);
        validateColumn(columns, request.columnKey());
        BoardCardEntity card = boardCardRepository.findById(cardId)
            .filter(item -> item.getProjectId().equals(projectId))
            .orElseThrow(() -> new IllegalArgumentException("Board card not found: " + cardId));

        if (!card.getColumnKey().equals(request.columnKey())) {
            int nextPosition = boardCardRepository
                .findByProjectIdAndColumnKeyOrderByPositionAsc(projectId, request.columnKey())
                .stream()
                .mapToInt(BoardCardEntity::getPosition)
                .max()
                .orElse(-1) + 1;
            card.setColumnKey(request.columnKey());
            card.setPosition(nextPosition);
        }
        card.setTitle(request.title());
        card.setDescription(request.description());
        card.setOwner(request.owner());
        card.setDueDate(request.dueDate());
        card.setPriority(normalizePriority(request.priority()));
        card.setLabels(normalizeLabels(request.labels()));
        card.setEstimate(request.estimate());
        card.setBlocked(request.blocked());
        return toCard(boardCardRepository.save(card));
    }

    public void deleteCard(String projectId, String cardId) {
        projectService.requireProject(projectId);
        BoardCardEntity card = boardCardRepository.findById(cardId)
            .filter(item -> item.getProjectId().equals(projectId))
            .orElseThrow(() -> new IllegalArgumentException("Board card not found: " + cardId));
        boardCardRepository.delete(card);
    }

    private List<BoardColumn> columnsFor(ProjectEntity project) {
        return columnsFor(normalizeDeliveryModel(project.getDeliveryModel()), project);
    }

    private List<BoardColumn> columnsFor(ProjectEntity project, String boardModel) {
        String deliveryModel = normalizeDeliveryModel(
            boardModel == null || boardModel.isBlank() ? project.getDeliveryModel() : boardModel
        );
        return columnsFor(deliveryModel, project);
    }

    private List<BoardColumn> columnsFor(String deliveryModel, ProjectEntity project) {
        return switch (deliveryModel) {
            case "scrum" -> scrumBoard(project);
            case "waterfall" -> waterfallBoard(project);
            default -> kanbanBoard(project);
        };
    }

    private void validateColumn(List<BoardColumn> columns, String columnKey) {
        boolean exists = columns.stream().anyMatch(column -> column.key().equals(columnKey));
        if (!exists) {
            throw new IllegalArgumentException("Board column not found: " + columnKey);
        }
    }

    private List<BoardColumn> kanbanBoard(ProjectEntity project) {
        return List.of(
            new BoardColumn("backlog", "Backlog", null, List.of()),
            new BoardColumn("ready", "Ready", 3, List.of()),
            new BoardColumn("in-progress", "In progress", 3, List.of()),
            new BoardColumn("review", "Review", 2, List.of()),
            new BoardColumn("done", "Done", null, List.of()));
    }

    private List<BoardColumn> scrumBoard(ProjectEntity project) {
        return List.of(
            new BoardColumn("product-backlog", "Product backlog", null, List.of()),
            new BoardColumn("sprint-ready", "Sprint ready", 5, List.of()),
            new BoardColumn("in-sprint", "In sprint", 4, List.of()),
            new BoardColumn("review", "Sprint review", 3, List.of()),
            new BoardColumn("retrospective", "Retrospective", null, List.of()));
    }

    private List<BoardColumn> waterfallBoard(ProjectEntity project) {
        return List.of(
            new BoardColumn("initiation", "Initiation", null, List.of()),
            new BoardColumn("planning", "Planning", null, List.of()),
            new BoardColumn("execution", "Execution", null, List.of()),
            new BoardColumn("verification", "Verification", null, List.of()),
            new BoardColumn("release", "Release", null, List.of()));
    }
    private List<BoardColumn> mergeCards(List<BoardColumn> templateColumns, List<BoardCardEntity> cards) {
        Map<String, List<BoardCard>> cardsByColumn = cards.stream()
            .sorted(Comparator.comparingInt(BoardCardEntity::getPosition))
            .map(entity -> Map.entry(entity.getColumnKey(), toCard(entity)))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));

        return templateColumns.stream()
            .map(column -> new BoardColumn(
                column.key(),
                column.title(),
                column.wipLimit(),
                cardsByColumn.getOrDefault(column.key(), List.of())
            ))
            .toList();
    }

    private BoardCard toCard(BoardCardEntity entity) {
        return new BoardCard(
            entity.getId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getOwner(),
            entity.getDueDate(),
            normalizePriority(entity.getPriority()),
            normalizeLabels(entity.getLabels()),
            entity.getEstimate() == null ? 0 : entity.getEstimate(),
            Boolean.TRUE.equals(entity.getBlocked())
        );
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "medium";
        }
        return switch (priority.trim().toLowerCase()) {
            case "low", "medium", "high", "critical" -> priority.trim().toLowerCase();
            default -> throw new IllegalArgumentException("Unsupported priority: " + priority);
        };
    }

    private String normalizeLabels(String labels) {
        return labels == null ? "" : labels.trim();
    }

    private String boardTitle(String deliveryModel) {
        return switch (deliveryModel) {
            case "scrum" -> "Scrum-доска";
            case "waterfall" -> "Waterfall-план";
            default -> "Kanban-доска";
        };
    }

    private String normalizeDeliveryModel(String deliveryModel) {
        if (deliveryModel == null || deliveryModel.isBlank()) {
            return "kanban";
        }
        return switch (deliveryModel.trim().toLowerCase()) {
            case "scrum", "waterfall", "kanban" -> deliveryModel.trim().toLowerCase();
            default -> throw new IllegalArgumentException("Unsupported delivery model: " + deliveryModel);
        };
    }
}

