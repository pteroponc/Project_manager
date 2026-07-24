package project_manager.web;

import project_manager.service.ReleaseChecksService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReleaseChecksController {
    private final ReleaseChecksService releaseChecksService;

    public ReleaseChecksController(ReleaseChecksService releaseChecksService) {
        this.releaseChecksService = releaseChecksService;
    }

    @GetMapping("/release-checks")
    public ReleaseChecksService.ReleaseChecksSnapshot releaseChecks(
        @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return releaseChecksService.getSnapshot(refresh);
    }
}
