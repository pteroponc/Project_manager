package project_manager.domain;

import java.util.List;

public record ReleaseTrainSnapshot(
    int trainCount,
    int activeTrainCount,
    int averageReadiness,
    int totalCapacityPoints,
    int totalCommittedPoints,
    int blockedItems,
    List<ReleaseTrainEntity> trains
) {
}
