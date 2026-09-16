package project_manager.service;

import project_manager.domain.ReleaseTrainEntity;
import project_manager.domain.ReleaseTrainSnapshot;
import project_manager.repository.ReleaseTrainRepository;
import project_manager.web.dto.ReleaseTrainRequest;
import project_manager.web.dto.ReleaseTrainResponse;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ReleaseTrainService {
    private final ReleaseTrainRepository releaseTrainRepository;

    public ReleaseTrainService(ReleaseTrainRepository releaseTrainRepository) {
        this.releaseTrainRepository = releaseTrainRepository;
    }

    public ReleaseTrainSnapshot snapshot(String quarter, String status) {
        List<ReleaseTrainEntity> trains = findTrains(quarter, status);
        int totalCapacity = trains.stream().mapToInt(ReleaseTrainEntity::getCapacityPoints).sum();
        int totalCommitted = trains.stream().mapToInt(ReleaseTrainEntity::getCommittedPoints).sum();
        int blocked = trains.stream().mapToInt(ReleaseTrainEntity::getBlockedItems).sum();
        int readiness = trains.isEmpty()
            ? 0
            : (int) Math.round(trains.stream().mapToInt(ReleaseTrainEntity::getReadiness).average().orElse(0));
        int active = (int) trains.stream()
            .filter(train -> !"released".equalsIgnoreCase(train.getStatus()))
            .count();

        return new ReleaseTrainSnapshot(
            trains.size(),
            active,
            readiness,
            totalCapacity,
            totalCommitted,
            blocked,
            trains
        );
    }

    public List<ReleaseTrainResponse> getTrains(String quarter, String status) {
        return findTrains(quarter, status).stream()
            .map(this::toResponse)
            .toList();
    }

    public ReleaseTrainResponse getTrain(String id) {
        return toResponse(requireTrain(id));
    }

    public ReleaseTrainResponse createTrain(ReleaseTrainRequest request) {
        ReleaseTrainEntity entity = new ReleaseTrainEntity();
        entity.setId(UUID.randomUUID().toString());
        applyRequest(entity, request);
        return toResponse(releaseTrainRepository.save(entity));
    }

    public ReleaseTrainResponse updateTrain(String id, ReleaseTrainRequest request) {
        ReleaseTrainEntity entity = requireTrain(id);
        applyRequest(entity, request);
        return toResponse(releaseTrainRepository.save(entity));
    }

    public void deleteTrain(String id) {
        releaseTrainRepository.delete(requireTrain(id));
    }

    private List<ReleaseTrainEntity> findTrains(String quarter, String status) {
        boolean allQuarter = "all".equalsIgnoreCase(quarter);
        boolean allStatus = "all".equalsIgnoreCase(status);

        if (allQuarter && allStatus) {
            return releaseTrainRepository.findAll();
        }
        if (allQuarter) {
            return releaseTrainRepository.findByStatusIgnoreCase(status);
        }
        if (allStatus) {
            return releaseTrainRepository.findByQuarterIgnoreCase(quarter);
        }
        return releaseTrainRepository.findByQuarterIgnoreCaseAndStatusIgnoreCase(quarter, status);
    }

    private ReleaseTrainEntity requireTrain(String id) {
        return releaseTrainRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Release train not found: " + id));
    }

    private void applyRequest(ReleaseTrainEntity entity, ReleaseTrainRequest request) {
        entity.setName(request.name());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setCadence(request.cadence());
        entity.setQuarter(request.quarter());
        entity.setPlannedReleaseDate(request.plannedReleaseDate());
        entity.setCodeFreezeDate(request.codeFreezeDate());
        entity.setQaFreezeDate(request.qaFreezeDate());
        entity.setGoLiveDate(request.goLiveDate());
        entity.setCapacityPoints(request.capacityPoints());
        entity.setCommittedPoints(request.committedPoints());
        entity.setReadiness(request.readiness());
        entity.setBlockedItems(request.blockedItems());
        entity.setScope(request.scope());
        entity.setRisk(request.risk());
        entity.setDecision(request.decision());
    }

    private ReleaseTrainResponse toResponse(ReleaseTrainEntity entity) {
        return new ReleaseTrainResponse(
            entity.getId(),
            entity.getName(),
            entity.getStatus(),
            entity.getCadence(),
            entity.getQuarter(),
            entity.getPlannedReleaseDate(),
            entity.getCodeFreezeDate(),
            entity.getQaFreezeDate(),
            entity.getGoLiveDate(),
            entity.getCapacityPoints(),
            entity.getCommittedPoints(),
            entity.getReadiness(),
            entity.getBlockedItems(),
            entity.getScope(),
            entity.getRisk(),
            entity.getDecision()
        );
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "planning";
        }
        return switch (status.trim().toLowerCase()) {
            case "planning", "boarding", "frozen", "qa", "released" -> status.trim().toLowerCase();
            default -> throw new IllegalArgumentException("Unsupported release train status: " + status);
        };
    }
}
