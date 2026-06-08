package project_manager.domain;

import java.util.List;

public record BoardColumn(
    String key,
    String title,
    Integer wipLimit,
    List<BoardCard> cards
) {
}
