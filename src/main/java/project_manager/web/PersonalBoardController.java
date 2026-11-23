package project_manager.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project_manager.service.PersonalBoardReadService;
import project_manager.service.BoardService;
import project_manager.domain.BoardCard;
import project_manager.web.dto.PersonalBoardResponse;
import project_manager.web.dto.PersonalTaskRequest;

@RestController
@RequestMapping("/api/board")
public class PersonalBoardController {
    private final PersonalBoardReadService reads;
    private final BoardService writes;

    public PersonalBoardController(PersonalBoardReadService reads, BoardService writes) {
        this.reads = reads;
        this.writes = writes;
    }

    @GetMapping("/personal")
    public PersonalBoardResponse personalBoard() {
        return reads.read();
    }

    @PostMapping("/personal/tasks")
    public BoardCard create(@RequestBody PersonalTaskRequest request) {
        return writes.createPersonalTask(request);
    }

    @PutMapping("/personal/tasks/{id}")
    public BoardCard update(@PathVariable String id, @RequestBody PersonalTaskRequest request) {
        return writes.updatePersonalTask(id, request);
    }
}
