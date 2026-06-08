package project_manager.web;

import project_manager.domain.PortfolioSnapshot;
import project_manager.service.PortfolioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PortfolioController {
    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/portfolio")
    public PortfolioSnapshot portfolio(
        @RequestParam(defaultValue = "all") String quarter,
        @RequestParam(defaultValue = "all") String health
    ) {
        return portfolioService.getSnapshot(quarter, health);
    }
}
