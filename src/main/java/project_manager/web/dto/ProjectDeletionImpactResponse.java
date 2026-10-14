package project_manager.web.dto;

import java.util.List;

public record ProjectDeletionImpactResponse(
    String id,
    String name,
    long taskCount,
    long milestoneCount,
    boolean deletionAllowed,
    List<String> blockers,
    long architecturePassportCount,
    long componentCount,
    long integrationCount,
    long checkCount,
    long securityRiskCount,
    boolean architectureImpactPreliminary
) { }
