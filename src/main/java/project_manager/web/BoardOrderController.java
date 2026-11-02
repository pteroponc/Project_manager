package project_manager.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project_manager.service.BoardOrderService;
import project_manager.web.dto.BoardOrderResponse;
import project_manager.web.dto.ProjectMutationError;

@RestController
@RequestMapping("/api/projects/{projectId}/board/kanban/columns/{columnKey}")
public class BoardOrderController {
    private final BoardOrderService boardOrderService;

    public BoardOrderController(BoardOrderService boardOrderService) {
        this.boardOrderService = boardOrderService;
    }

    @GetMapping("/order")
    public BoardOrderResponse order(@PathVariable String projectId, @PathVariable String columnKey) {
        return boardOrderService.order(projectId, columnKey);
    }

    @PostMapping("/cards/{cardId}/reorder")
    public BoardOrderResponse reorder(@PathVariable String projectId, @PathVariable String columnKey,
                                      @PathVariable String cardId, @RequestBody JsonNode request) {
        return boardOrderService.reorder(projectId, columnKey, cardId, request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProjectMutationError handleOptimisticConflict(ObjectOptimisticLockingFailureException exception) {
        return new ProjectMutationError("BOARD_ORDER_CONFLICT", "Board order changed; reread column", null, null);
    }
}
