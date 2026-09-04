package project_manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "release_trains")
public class ReleaseTrainEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String cadence;

    @Column(nullable = false)
    private String quarter;

    @Column(nullable = false)
    private String plannedReleaseDate;

    @Column(nullable = false)
    private String codeFreezeDate;

    @Column(nullable = false)
    private String qaFreezeDate;

    @Column(nullable = false)
    private String goLiveDate;

    @Column(nullable = false)
    private int capacityPoints;

    @Column(nullable = false)
    private int committedPoints;

    @Column(nullable = false)
    private int readiness;

    @Column(nullable = false)
    private int blockedItems;

    @Column(nullable = false, length = 10000)
    private String scope;

    @Column(nullable = false, length = 10000)
    private String risk;

    @Column(nullable = false, length = 10000)
    private String decision;

    public ReleaseTrainEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCadence() {
        return cadence;
    }

    public void setCadence(String cadence) {
        this.cadence = cadence;
    }

    public String getQuarter() {
        return quarter;
    }

    public void setQuarter(String quarter) {
        this.quarter = quarter;
    }

    public String getPlannedReleaseDate() {
        return plannedReleaseDate;
    }

    public void setPlannedReleaseDate(String plannedReleaseDate) {
        this.plannedReleaseDate = plannedReleaseDate;
    }

    public String getCodeFreezeDate() {
        return codeFreezeDate;
    }

    public void setCodeFreezeDate(String codeFreezeDate) {
        this.codeFreezeDate = codeFreezeDate;
    }

    public String getQaFreezeDate() {
        return qaFreezeDate;
    }

    public void setQaFreezeDate(String qaFreezeDate) {
        this.qaFreezeDate = qaFreezeDate;
    }

    public String getGoLiveDate() {
        return goLiveDate;
    }

    public void setGoLiveDate(String goLiveDate) {
        this.goLiveDate = goLiveDate;
    }

    public int getCapacityPoints() {
        return capacityPoints;
    }

    public void setCapacityPoints(int capacityPoints) {
        this.capacityPoints = capacityPoints;
    }

    public int getCommittedPoints() {
        return committedPoints;
    }

    public void setCommittedPoints(int committedPoints) {
        this.committedPoints = committedPoints;
    }

    public int getReadiness() {
        return readiness;
    }

    public void setReadiness(int readiness) {
        this.readiness = readiness;
    }

    public int getBlockedItems() {
        return blockedItems;
    }

    public void setBlockedItems(int blockedItems) {
        this.blockedItems = blockedItems;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}
