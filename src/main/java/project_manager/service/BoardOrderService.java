package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project_manager.domain.BoardCardEntity;
import project_manager.repository.BoardCardRepository;
import project_manager.repository.ProjectRepository;
import project_manager.web.ProjectMutationException;
import project_manager.web.dto.BoardOrderResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BoardOrderService {
    private static final Set<String> KANBAN_COLUMNS = Set.of(
        "backlog", "ready", "in-progress", "review", "done");
    private static final Comparator<BoardCardEntity> ORDER = Comparator
        .comparingInt(BoardCardEntity::getPosition).thenComparing(BoardCardEntity::getId);

    private final ProjectRepository projects;
    private final BoardCardRepository cards;
    private final ProjectWriteLock projectWriteLock;
    private final EntityManager entityManager;

    public BoardOrderService(ProjectRepository projects, BoardCardRepository cards,
                             ProjectWriteLock projectWriteLock, EntityManager entityManager) {
        this.projects = projects;
        this.cards = cards;
        this.projectWriteLock = projectWriteLock;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public BoardOrderResponse order(String projectId, String columnKey) {
        requireColumn(columnKey);
        if (!projects.existsById(projectId)) throw ProjectMutationException.projectNotFound();
        return response(projectId, columnKey, null, currentOrder(projectId, columnKey));
    }

    @Transactional
    public BoardOrderResponse reorder(String projectId, String columnKey, String cardId, JsonNode body) {
        requireColumn(columnKey);
        Request request = parseRequest(cardId, body);
        projectWriteLock.require(projectId);
        BoardCardEntity target = cards.findById(cardId)
            .filter(card -> card.getProjectId().equals(projectId))
            .orElseThrow(ProjectMutationException::boardCardNotFound);
        if (!target.getColumnKey().equals(columnKey)) throw ProjectMutationException.boardOrderConflict();

        List<BoardCardEntity> current = currentOrder(projectId, columnKey);
        if (!matches(current, request.expectedOrder())) throw ProjectMutationException.boardOrderConflict();
        int source = indexOf(current, cardId);
        int neighbor = source + (request.direction().equals("up") ? -1 : 1);
        if (neighbor < 0 || neighbor >= current.size()) {
            return response(projectId, columnKey, false, current);
        }

        List<BoardCardEntity> desired = new ArrayList<>(current);
        java.util.Collections.swap(desired, source, neighbor);
        Map<String, Integer> oldIndices = new HashMap<>();
        for (int index = 0; index < current.size(); index++) {
            oldIndices.put(current.get(index).getId(), index);
        }

        List<BoardCardEntity> forceVersion = new ArrayList<>();
        for (int index = 0; index < desired.size(); index++) {
            BoardCardEntity card = desired.get(index);
            if (card.getPosition() != index) {
                card.setPosition(index);
            } else if (oldIndices.get(card.getId()) != index) {
                forceVersion.add(card);
            }
        }
        // Flush all changed positions first. Their managed @Version values advance once.
        cards.flush();
        // Equal old positions can change logical order while one physical position stays equal.
        // Force only those clean entities, never an entity already updated by JPA.
        for (BoardCardEntity card : forceVersion) {
            if (cards.incrementVersionIfCurrent(card.getId(), card.getVersion()) != 1) {
                throw ProjectMutationException.boardOrderConflict();
            }
            entityManager.refresh(card);
        }
        return response(projectId, columnKey, true, desired);
    }

    private void requireColumn(String columnKey) {
        if (!KANBAN_COLUMNS.contains(columnKey)) {
            throw ProjectMutationException.invalid("columnKey", "Unknown Kanban column");
        }
    }

    private List<BoardCardEntity> currentOrder(String projectId, String columnKey) {
        return cards.findByProjectIdAndColumnKeyOrderByPositionAsc(projectId, columnKey).stream()
            .sorted(ORDER).toList();
    }

    private boolean matches(List<BoardCardEntity> current, List<BoardOrderResponse.Card> expected) {
        if (current.size() != expected.size()) return false;
        for (int index = 0; index < current.size(); index++) {
            BoardCardEntity actual = current.get(index);
            BoardOrderResponse.Card sent = expected.get(index);
            if (!actual.getId().equals(sent.id()) || actual.getPosition() != sent.position()
                || actual.getVersion() != sent.version()) return false;
        }
        return true;
    }

    private int indexOf(List<BoardCardEntity> current, String cardId) {
        for (int index = 0; index < current.size(); index++) {
            if (current.get(index).getId().equals(cardId)) return index;
        }
        throw ProjectMutationException.boardOrderConflict();
    }

    private BoardOrderResponse response(String projectId, String columnKey, Boolean changed,
                                        List<BoardCardEntity> ordered) {
        return new BoardOrderResponse(projectId, columnKey, changed, ordered.stream()
            .map(card -> new BoardOrderResponse.Card(card.getId(), card.getPosition(), card.getVersion()))
            .toList());
    }

    private Request parseRequest(String cardId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ProjectMutationException.invalid("expectedOrder", "Required order snapshot");
        }
        JsonNode direction = body.get("direction");
        if (direction == null || !direction.isTextual()
            || !(direction.textValue().equals("up") || direction.textValue().equals("down"))) {
            throw ProjectMutationException.invalid("direction", "Must be up or down");
        }
        JsonNode entries = body.get("expectedOrder");
        if (entries == null || !entries.isArray()) {
            throw ProjectMutationException.invalid("expectedOrder", "Required order snapshot");
        }
        List<BoardOrderResponse.Card> expected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode entry : entries) {
            if (!entry.isObject()) throw ProjectMutationException.invalid("expectedOrder", "Invalid order entry");
            JsonNode id = entry.get("id");
            JsonNode position = entry.get("position");
            JsonNode version = entry.get("version");
            if (id == null || !id.isTextual() || id.textValue().isBlank() || !ids.add(id.textValue())
                || position == null || !position.isIntegralNumber() || !position.canConvertToInt()
                || version == null || !version.isIntegralNumber() || !version.canConvertToLong()
                || version.longValue() < 0) {
                throw ProjectMutationException.invalid("expectedOrder", "Invalid order entry");
            }
            expected.add(new BoardOrderResponse.Card(id.textValue(), position.intValue(), version.longValue()));
        }
        if (!ids.contains(cardId)) {
            throw ProjectMutationException.invalid("expectedOrder", "Target card must occur once");
        }
        return new Request(direction.textValue(), List.copyOf(expected));
    }

    private record Request(String direction, List<BoardOrderResponse.Card> expectedOrder) { }
}
