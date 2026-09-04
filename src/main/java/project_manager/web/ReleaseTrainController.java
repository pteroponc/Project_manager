package project_manager.web;

import project_manager.domain.ReleaseTrainSnapshot;
import project_manager.service.ReleaseTrainService;
import project_manager.web.dto.ReleaseTrainRequest;
import project_manager.web.dto.ReleaseTrainResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReleaseTrainController {
    private final ReleaseTrainService releaseTrainService;

    public ReleaseTrainController(ReleaseTrainService releaseTrainService) {
        this.releaseTrainService = releaseTrainService;
    }

    @GetMapping("/release-trains")
    public List<ReleaseTrainResponse> trains(
        @RequestParam(defaultValue = "all") String quarter,
        @RequestParam(defaultValue = "all") String status
    ) {
        return releaseTrainService.getTrains(quarter, status);
    }

    @GetMapping("/release-trains/snapshot")
    public ReleaseTrainSnapshot snapshot(
        @RequestParam(defaultValue = "all") String quarter,
        @RequestParam(defaultValue = "all") String status
    ) {
        return releaseTrainService.snapshot(quarter, status);
    }

    @GetMapping("/release-trains/{id}")
    public ReleaseTrainResponse train(@PathVariable String id) {
        return releaseTrainService.getTrain(id);
    }

    @PostMapping("/release-trains")
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseTrainResponse create(@Valid @RequestBody ReleaseTrainRequest request) {
        return releaseTrainService.createTrain(request);
    }

    @PutMapping("/release-trains/{id}")
    public ReleaseTrainResponse update(@PathVariable String id, @Valid @RequestBody ReleaseTrainRequest request) {
        return releaseTrainService.updateTrain(id, request);
    }

    @DeleteMapping("/release-trains/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        releaseTrainService.deleteTrain(id);
    }
}
