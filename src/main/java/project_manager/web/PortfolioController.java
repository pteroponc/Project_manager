package project_manager.web;

import project_manager.domain.PortfolioSnapshot;
import project_manager.domain.PortfolioOverview;
import project_manager.service.PortfolioService;
import project_manager.service.PortfolioOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final PortfolioOverviewService portfolioOverviewService;

    public PortfolioController(PortfolioService portfolioService, PortfolioOverviewService portfolioOverviewService) {
        this.portfolioService = portfolioService;
        this.portfolioOverviewService = portfolioOverviewService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/portfolio")
    public PortfolioSnapshot portfolio(
        @RequestParam(defaultValue = "all") String quarter,
        @RequestParam(defaultValue = "all") String health,
        @RequestParam(defaultValue = "all") String status
    ) {
        return portfolioService.getSnapshot(quarter, health, status);
    }

    @GetMapping("/portfolio/overview")
    public PortfolioOverview overview(
        @RequestParam(defaultValue = "all") String quarter,
        @RequestParam(defaultValue = "all") String health,
        @RequestParam(defaultValue = "all") String status
    ) {
        return portfolioOverviewService.getOverview(quarter, health, status);
    }
}
