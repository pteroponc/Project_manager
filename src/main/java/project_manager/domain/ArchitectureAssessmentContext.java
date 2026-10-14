package project_manager.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Capture once per request and reuse for every project in that request. */
public record ArchitectureAssessmentContext(LocalDate calculationDate) {
    public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    public ArchitectureAssessmentContext {
        Objects.requireNonNull(calculationDate, "Calculation date is required");
    }

    public String timeZone() { return ZONE.getId(); }
}
