package project_manager.domain;

import java.util.List;
import java.util.Map;

public record ProjectBoard(
    String projectId,
    String projectName,
    String deliveryModel,
    String boardTitle,
    List<BoardColumn> columns,
    List<BoardColumn> unassignedColumns,
    Map<String, Integer> storedPositions
) {
}
