package project_manager.domain;

import java.util.List;

public record ProjectBoard(
    String projectId,
    String projectName,
    String deliveryModel,
    String boardTitle,
    List<BoardColumn> columns
) {
}
