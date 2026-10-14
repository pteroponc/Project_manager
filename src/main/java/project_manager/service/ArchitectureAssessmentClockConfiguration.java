package project_manager.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import project_manager.domain.ArchitectureAssessmentContext;

import java.time.Clock;

@Configuration
public class ArchitectureAssessmentClockConfiguration {
    @Bean("architectureAssessmentClock")
    public Clock architectureAssessmentClock() {
        return Clock.system(ArchitectureAssessmentContext.ZONE);
    }
}
