package project_manager.web;

import project_manager.web.dto.ArchitectureReadDtos.PassportDeletionImpact;

/** Safe conflict containing counts/reasons only, without entity contents or DB exceptions. */
public class ArchitecturePassportDependenciesException extends RuntimeException {
    private final PassportDeletionImpact impact;
    private final long version;
    public ArchitecturePassportDependenciesException(PassportDeletionImpact impact, long version) {
        super("Passport has dependencies"); this.impact = impact; this.version = version;
    }
    public PassportDeletionImpact impact() { return impact; }
    public long version() { return version; }
}
