package project_manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ArchitectureEnvironmentId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 255)
    private String projectId;

    @Column(name = "environment_code", nullable = false, length = 16)
    private String environmentCode;

    public ArchitectureEnvironmentId() {}

    public ArchitectureEnvironmentId(String projectId, String environmentCode) {
        this.projectId = projectId;
        this.environmentCode = environmentCode;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { projectId = value; }

    public String getEnvironmentCode() { return environmentCode; }
    public void setEnvironmentCode(String value) { environmentCode = value; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ArchitectureEnvironmentId value)) return false;
        return Objects.equals(projectId, value.projectId) && Objects.equals(environmentCode, value.environmentCode);
    }

    @Override
    public int hashCode() { return Objects.hash(projectId, environmentCode); }
}
