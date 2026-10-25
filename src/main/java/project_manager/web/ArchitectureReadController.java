package project_manager.web;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import project_manager.service.ArchitectureReadService;
import project_manager.web.dto.ArchitecturePortfolioQuery;
import project_manager.web.dto.ArchitecturePortfolioSummaryQuery;
import project_manager.web.dto.ArchitectureReadDtos.*;

@RestController
@RequestMapping("/api")
public class ArchitectureReadController {
    private final ArchitectureReadService reads;
    public ArchitectureReadController(ArchitectureReadService reads) { this.reads = reads; }

    @GetMapping("/projects/{id}/architecture")
    public Card card(@PathVariable String id) { return reads.card(id); }

    @GetMapping("/architecture/portfolio")
    public Portfolio portfolio(@RequestParam MultiValueMap<String, String> parameters) {
        if (parameters.values().stream().anyMatch(values -> values.size() != 1)) throw ArchitecturePortfolioQuery.invalid();
        return reads.portfolio(parameters.toSingleValueMap());
    }

    @GetMapping("/architecture/portfolio/summary")
    public PortfolioSummary summary(@RequestParam MultiValueMap<String, String> parameters) {
        if (parameters.values().stream().anyMatch(values -> values.size() != 1)) throw ArchitecturePortfolioSummaryQuery.invalid();
        return reads.portfolioSummary(parameters.toSingleValueMap());
    }

    @GetMapping("/projects/{id}/architecture/deletion-impact")
    public PassportDeletionImpact impact(@PathVariable String id) { return reads.passportImpact(id); }
}
