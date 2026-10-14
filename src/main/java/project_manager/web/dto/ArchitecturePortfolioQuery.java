package project_manager.web.dto;

import project_manager.web.ProjectMutationException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record ArchitecturePortfolioQuery(String query, String quarter, String projectStatus, String readiness,
    String criticality, String sort, String direction, int page, int size) {
    private static final Set<String> PARAMETERS = Set.of("query", "quarter", "projectStatus", "readiness", "criticality",
        "sort", "direction", "page", "size");

    public static ArchitecturePortfolioQuery parse(Map<String, String> parameters) {
        if (!PARAMETERS.containsAll(parameters.keySet())) throw invalid();
        String status = allowed(parameters, "projectStatus", "all", Set.of("all", "planned", "active", "paused", "done"));
        String readiness = allowed(parameters, "readiness", "all", Set.of("all", "blocked", "attention_required", "not_assessed", "ready"));
        String criticality = allowed(parameters, "criticality", "all", Set.of("all", "low", "medium", "high", "critical", "unspecified"));
        String sort = allowed(parameters, "sort", "name", Set.of("name", "owner", "quarter", "projectstatus", "readiness", "criticality"));
        String direction = allowed(parameters, "direction", "asc", Set.of("asc", "desc"));
        int page = number(parameters.getOrDefault("page", "0"));
        int size = number(parameters.getOrDefault("size", "20"));
        if (page < 0 || size < 1 || size > 100) throw invalid();
        String quarter = normalized(parameters.getOrDefault("quarter", "all"));
        if (quarter.isEmpty()) throw invalid();
        return new ArchitecturePortfolioQuery(normalized(parameters.getOrDefault("query", "")), quarter,
            status, readiness, criticality, sort, direction, page, size);
    }

    private static String allowed(Map<String, String> p, String name, String fallback, Set<String> allowed) {
        String value = normalized(p.getOrDefault(name, fallback));
        if (!allowed.contains(value)) throw invalid();
        return value;
    }
    private static int number(String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException | NullPointerException error) { throw invalid(); }
    }
    private static String normalized(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public static ProjectMutationException invalid() { return ProjectMutationException.badRequest("Invalid architecture portfolio parameters"); }
}
