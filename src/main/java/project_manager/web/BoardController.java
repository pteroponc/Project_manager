package project_manager.web;

import project_manager.domain.ProjectBoard;
import project_manager.domain.BoardCard;
import project_manager.service.BoardService;
import project_manager.web.dto.BoardCardRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BoardController {
    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/delivery-models")
    public Map<String, List<String>> deliveryModels() {
        return Map.of("items", boardService.getAvailableDeliveryModels());
    }

    @GetMapping("/projects/{id}/board")
    public ProjectBoard projectBoard(
        @PathVariable String id,
        @RequestParam(required = false) String model
    ) {
        return boardService.getBoard(id, model);
    }

    @PostMapping("/projects/{projectId}/board/cards")
    public BoardCard createBoardCard(
        @PathVariable String projectId,
        @RequestParam(required = false) String model,
        @Valid @RequestBody BoardCardRequest request
    ) {
        return boardService.createCard(projectId, request, model);
    }

    @PutMapping("/projects/{projectId}/board/cards/{cardId}")
    public BoardCard updateBoardCard(
        @PathVariable String projectId,
        @PathVariable String cardId,
        @RequestParam(required = false) String model,
        @Valid @RequestBody BoardCardRequest request
    ) {
        return boardService.updateCard(projectId, cardId, request, model);
    }

    @DeleteMapping("/projects/{projectId}/board/cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBoardCard(@PathVariable String projectId, @PathVariable String cardId) {
        boardService.deleteCard(projectId, cardId);
    }
}
