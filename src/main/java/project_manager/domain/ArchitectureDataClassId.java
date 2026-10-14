package project_manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ArchitectureDataClassId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 255)
    private String projectId;

    @Column(name = "class_code", nullable = false, length = 32)
    private String classCode;

    public ArchitectureDataClassId() {}

    public ArchitectureDataClassId(String projectId, String classCode) {
        this.projectId = projectId;
        this.classCode = classCode;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { projectId = value; }

    public String getClassCode() { return classCode; }
    public void setClassCode(String value) { classCode = value; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ArchitectureDataClassId value)) return false;
        return Objects.equals(projectId, value.projectId) && Objects.equals(classCode, value.classCode);
    }

    @Override
    public int hashCode() { return Objects.hash(projectId, classCode); }
}
