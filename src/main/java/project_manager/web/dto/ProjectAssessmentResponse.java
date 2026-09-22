package project_manager.web.dto;

import project_manager.service.ProjectAssessmentService.Assessment;

import java.util.List;

public record ProjectAssessmentResponse(
    Long daysUntilDeadline,
    boolean overdue,
    boolean requiresAttention,
    List<String> attentionReasons,
    List<String> dataQualityIssues
) {
    public static ProjectAssessmentResponse from(Assessment assessment) {
        return new ProjectAssessmentResponse(
            assessment.daysUntilDeadline(),
            assessment.overdue(),
            assessment.requiresAttention(),
            assessment.attentionReasons(),
            assessment.dataQualityIssues()
        );
    }
}
