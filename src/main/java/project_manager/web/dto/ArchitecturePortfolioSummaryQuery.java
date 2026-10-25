package project_manager.web.dto;

import project_manager.web.ProjectMutationException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registry-compatible query/quarter/status semantics; readiness is applied after facet counts. */
public record ArchitecturePortfolioSummaryQuery(String query, String quarter, String status, String readiness,
    String sort, String direction, int page, int size) {
    private static final Set<String> PARAMETERS = Set.of("query", "quarter", "status", "readiness",
        "sort", "direction", "page", "size");
    private static final Set<String> SORTS = Set.of("name", "deadline", "health", "owner", "quarter",
        "projectstatus", "readiness");

    public static ArchitecturePortfolioSummaryQuery parse(Map<String, String> parameters) {
        if (!PARAMETERS.containsAll(parameters.keySet())) throw invalid();
        String readiness = parameters.getOrDefault("readiness", "all").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("all", "blocked", "attention_required", "not_assessed", "ready").contains(readiness)) throw invalid();
        String sort = parameters.getOrDefault("sort", "name").trim().toLowerCase(Locale.ROOT);
        String direction = parameters.getOrDefault("direction", "asc").trim().toLowerCase(Locale.ROOT);
        if (!SORTS.contains(sort) || !Set.of("asc", "desc").contains(direction)) throw invalid();
        int page = number(parameters.getOrDefault("page", "0"));
        int size = number(parameters.getOrDefault("size", "20"));
        if (page < 0 || size < 1 || size > 100) throw invalid();
        return new ArchitecturePortfolioSummaryQuery(parameters.getOrDefault("query", "").trim().toLowerCase(Locale.ROOT),
            filter(parameters.get("quarter")), filter(parameters.get("status")), readiness, sort, direction, page, size);
    }

    // ProjectService/ProjectRepository preserve edge spaces for these exact-value filters.
    // Unknown quarter/status values yield an empty selection, including legacy stored codes.
    private static String filter(String value) {
        return value == null || "all".equalsIgnoreCase(value) ? "all" : value.toLowerCase(Locale.ROOT);
    }
    private static int number(String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException error) { throw invalid(); }
    }
    public static ProjectMutationException invalid() {
        return ProjectMutationException.badRequest("Invalid architecture portfolio summary parameters");
    }
}
