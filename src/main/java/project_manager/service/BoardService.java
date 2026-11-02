package project_manager.service;

import jakarta.persistence.EntityManager;
import project_manager.domain.BoardCard;
import project_manager.domain.BoardCardEntity;
import project_manager.domain.BoardColumn;
import project_manager.domain.ProjectBoard;
import project_manager.domain.ProjectEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.BoardCardRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BoardService {
    private final ProjectService projectService;
    private final BoardCardRepository boardCardRepository;
    private final ProjectWriteLock projectWriteLock;
    private final EntityManager entityManager;

    public BoardService(ProjectService projectService, BoardCardRepository boardCardRepository,
                        ProjectWriteLock projectWriteLock, EntityManager entityManager) {
        this.projectService = projectService;
        this.boardCardRepository = boardCardRepository;
        this.projectWriteLock = projectWriteLock;
        this.entityManager = entityManager;
    }

    public ProjectBoard getBoard(String projectId) {
        return getBoard(projectId, null);
    }

    public ProjectBoard getBoard(String projectId, String boardModel) {
        ProjectEntity project;
        try {
            project = projectService.requireProject(projectId);
        } catch (IllegalArgumentException exception) {
            throw ProjectMutationException.projectNotFound();
        }
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

    @Transactional
    public BoardCard createCard(String projectId, BoardCardRequest request) {
        return createCard(projectId, request, null);
    }

    @Transactional
    public BoardCard createCard(String projectId, BoardCardRequest request, String boardModel) {
        validateRequest(request);
        ProjectEntity project = projectWriteLock.require(projectId);
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
        return toCard(boardCardRepository.saveAndFlush(card));
    }

    @Transactional
    public BoardCard updateCard(String projectId, String cardId, BoardCardRequest request) {
        return updateCard(projectId, cardId, request, null);
    }

    @Transactional
    public BoardCard updateCard(String projectId, String cardId, BoardCardRequest request, String boardModel) {
        validateRequest(request);
        ProjectEntity project = projectWriteLock.require(projectId);
        BoardCardEntity card = boardCardRepository.findById(cardId)
            .filter(item -> item.getProjectId().equals(projectId))
            .orElseThrow(ProjectMutationException::boardCardNotFound);
        long expectedVersion = requireExpectedVersion(request.expectedVersion());
        checkVersion(card, expectedVersion);
        List<BoardColumn> columns = columnsFor(project, boardModel);
        validateColumn(columns, request.columnKey());

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
        applyCardRequest(card, request);
        return toCard(saveWithVersionBump(card, expectedVersion));
    }

    /** Service-level transfer for callers that move a card between projects. */
    @Transactional
    public BoardCard transferCard(String sourceProjectId, String targetProjectId, String cardId,
                                  BoardCardRequest request) {
        validateRequest(request);
        projectWriteLock.requireBoth(sourceProjectId, targetProjectId);
        ProjectEntity target = projectService.requireProject(targetProjectId);
        List<BoardColumn> columns = columnsFor(target);
        validateColumn(columns, request.columnKey());
        BoardCardEntity card = boardCardRepository.findById(cardId)
            .filter(item -> item.getProjectId().equals(sourceProjectId))
            .orElseThrow(ProjectMutationException::boardCardNotFound);
        long expectedVersion = requireExpectedVersion(request.expectedVersion());
        checkVersion(card, expectedVersion);
        int nextPosition = boardCardRepository
            .findByProjectIdAndColumnKeyOrderByPositionAsc(targetProjectId, request.columnKey())
            .stream().mapToInt(BoardCardEntity::getPosition).max().orElse(-1) + 1;
        card.setProjectId(targetProjectId);
        card.setColumnKey(request.columnKey());
        card.setPosition(nextPosition);
        applyCardRequest(card, request);
        return toCard(saveWithVersionBump(card, expectedVersion));
    }

    @Transactional
    public void deleteCard(String projectId, String cardId, String expectedVersionText) {
        projectWriteLock.require(projectId);
        BoardCardEntity card = boardCardRepository.findById(cardId)
            .filter(item -> item.getProjectId().equals(projectId))
            .orElseThrow(ProjectMutationException::boardCardNotFound);
        checkVersion(card, parseExpectedVersion(expectedVersionText));
        boardCardRepository.delete(card);
        boardCardRepository.flush();
    }

    private BoardCardEntity saveWithVersionBump(BoardCardEntity card, long expectedVersion) {
        BoardCardEntity saved = boardCardRepository.saveAndFlush(card);
        if (saved.getVersion() == expectedVersion) {
            if (boardCardRepository.incrementVersionIfCurrent(saved.getId(), expectedVersion) != 1) {
                throw ProjectMutationException.boardCardVersionConflict(saved.getVersion());
            }
            entityManager.refresh(saved);
        }
        return saved;
    }

    private long requireExpectedVersion(Long version) {
        if (version == null || version < 0) {
            throw ProjectMutationException.invalid("expectedVersion", "Required nonnegative version");
        }
        return version;
    }

    private long parseExpectedVersion(String version) {
        if (version == null || !version.matches("[0-9]+")) {
            throw ProjectMutationException.invalid("expectedVersion", "Required nonnegative version");
        }
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException exception) {
            throw ProjectMutationException.invalid("expectedVersion", "Required nonnegative version");
        }
    }

    private void checkVersion(BoardCardEntity card, long expectedVersion) {
        if (card.getVersion() != expectedVersion) {
            throw ProjectMutationException.boardCardVersionConflict(card.getVersion());
        }
    }

    private void applyCardRequest(BoardCardEntity card, BoardCardRequest request) {
        card.setTitle(request.title());
        card.setDescription(request.description());
        card.setOwner(request.owner());
        card.setDueDate(request.dueDate());
        card.setPriority(normalizePriority(request.priority()));
        card.setLabels(normalizeLabels(request.labels()));
        card.setEstimate(request.estimate());
        card.setBlocked(request.blocked());
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
            throw ProjectMutationException.invalid("columnKey", "Unknown board column");
        }
    }

    private void validateRequest(BoardCardRequest request) {
        if (request == null) throw ProjectMutationException.badRequest("Invalid board card request");
        requireText(request.title(), "title", 255);
        requireText(request.description(), "description", 10000);
        requireText(request.owner(), "owner", 255);
        requireText(request.dueDate(), "dueDate", 255);
        requireText(request.columnKey(), "columnKey", 255);
        requireText(request.priority(), "priority", 255);
        if (request.labels() != null && request.labels().length() > 255) {
            throw ProjectMutationException.invalid("labels", "Must be at most 255 characters");
        }
        if (request.estimate() < 0) throw ProjectMutationException.invalid("estimate", "Must be nonnegative");
        if (!request.dueDate().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw ProjectMutationException.invalid("dueDate", "Must be a real date in yyyy-MM-dd format");
        }
        try {
            LocalDate.parse(request.dueDate());
        } catch (DateTimeParseException exception) {
            throw ProjectMutationException.invalid("dueDate", "Must be a real date in yyyy-MM-dd format");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ProjectMutationException.invalid(field, "Required value");
        }
        if (value.length() > maxLength) {
            throw ProjectMutationException.invalid(field, "Must be at most " + maxLength + " characters");
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
            Boolean.TRUE.equals(entity.getBlocked()),
            entity.getVersion()
        );
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "medium";
        }
        return switch (priority.trim().toLowerCase()) {
            case "low", "medium", "high", "critical" -> priority.trim().toLowerCase();
            default -> throw ProjectMutationException.invalid("priority", "Unsupported priority");
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
            default -> throw ProjectMutationException.invalid("model", "Unsupported delivery model");
        };
    }
}

