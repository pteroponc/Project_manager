package project_manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IntegrationDataClassId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "integration_id", nullable = false, length = 36)
    private String integrationId;

    @Column(name = "class_code", nullable = false, length = 32)
    private String classCode;

    public IntegrationDataClassId() {}

    public IntegrationDataClassId(String integrationId, String classCode) {
        this.integrationId = integrationId;
        this.classCode = classCode;
    }

    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String value) { integrationId = value; }

    public String getClassCode() { return classCode; }
    public void setClassCode(String value) { classCode = value; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IntegrationDataClassId value)) return false;
        return Objects.equals(integrationId, value.integrationId) && Objects.equals(classCode, value.classCode);
    }

    @Override
    public int hashCode() { return Objects.hash(integrationId, classCode); }
}
