package project_manager.web.dto;

import java.util.List;

public record ProjectDeletionImpactResponse(
    String id,
    String name,
    long taskCount,
    boolean deletionAllowed,
    List<String> blockers
) { }
