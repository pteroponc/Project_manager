package project_manager.service;

import project_manager.domain.ReleaseTrainEntity;
import project_manager.domain.ReleaseTrainSnapshot;
import project_manager.repository.ReleaseTrainRepository;
import project_manager.web.dto.ReleaseTrainRequest;
import project_manager.web.dto.ReleaseTrainResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReleaseTrainService {
    private final ReleaseTrainRepository releaseTrainRepository;

    public ReleaseTrainService(ReleaseTrainRepository releaseTrainRepository) {
        this.releaseTrainRepository = releaseTrainRepository;
    }

    @PostConstruct
    void seed() {
        List<ReleaseTrainEntity> demoTrains = demoTrains();
        if (releaseTrainRepository.count() > 0) {
            refreshDemoTrains(demoTrains);
            return;
        }

        releaseTrainRepository.saveAll(demoTrains);
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

    private List<ReleaseTrainEntity> demoTrains() {
        LocalDate today = LocalDate.now();
        return List.of(
            buildTrain(
                "RT-2026-Q3 Platform",
                "boarding",
                "2 weeks",
                "Q3 2026",
                today.plusDays(35).toString(),
                today.plusDays(21).toString(),
                today.plusDays(28).toString(),
                today.plusDays(38).toString(),
                80,
                68,
                74,
                2,
                "Digital Commerce Platform, Mobile Workforce Rollout",
                "ERP API и MDM-интеграция могут не войти в окно code freeze.",
                "До freeze подтвердить владельцев интеграций и снять два блокера."
            ),
            buildTrain(
                "RT-2026-Q3 Knowledge",
                "planning",
                "monthly",
                "Q3 2026",
                today.plusDays(63).toString(),
                today.plusDays(45).toString(),
                today.plusDays(54).toString(),
                today.plusDays(66).toString(),
                55,
                37,
                58,
                1,
                "Knowledge Hub Migration",
                "Не все домены знаний имеют владельцев и критерии готовности.",
                "Закрыть карту владельцев до начала boarding."
            )
        );
    }

    private ReleaseTrainEntity buildTrain(
        String name,
        String status,
        String cadence,
        String quarter,
        String plannedReleaseDate,
        String codeFreezeDate,
        String qaFreezeDate,
        String goLiveDate,
        int capacityPoints,
        int committedPoints,
        int readiness,
        int blockedItems,
        String scope,
        String risk,
        String decision
    ) {
        ReleaseTrainEntity entity = new ReleaseTrainEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name);
        entity.setStatus(normalizeStatus(status));
        entity.setCadence(cadence);
        entity.setQuarter(quarter);
        entity.setPlannedReleaseDate(plannedReleaseDate);
        entity.setCodeFreezeDate(codeFreezeDate);
        entity.setQaFreezeDate(qaFreezeDate);
        entity.setGoLiveDate(goLiveDate);
        entity.setCapacityPoints(capacityPoints);
        entity.setCommittedPoints(committedPoints);
        entity.setReadiness(readiness);
        entity.setBlockedItems(blockedItems);
        entity.setScope(scope);
        entity.setRisk(risk);
        entity.setDecision(decision);
        return entity;
    }

    private void refreshDemoTrains(List<ReleaseTrainEntity> demoTrains) {
        for (ReleaseTrainEntity demoTrain : demoTrains) {
            releaseTrainRepository.findByName(demoTrain.getName()).ifPresent(existing -> {
                String id = existing.getId();
                copyTrainFields(demoTrain, existing);
                existing.setId(id);
                releaseTrainRepository.save(existing);
            });
        }
    }

    private void copyTrainFields(ReleaseTrainEntity source, ReleaseTrainEntity target) {
        target.setName(source.getName());
        target.setStatus(source.getStatus());
        target.setCadence(source.getCadence());
        target.setQuarter(source.getQuarter());
        target.setPlannedReleaseDate(source.getPlannedReleaseDate());
        target.setCodeFreezeDate(source.getCodeFreezeDate());
        target.setQaFreezeDate(source.getQaFreezeDate());
        target.setGoLiveDate(source.getGoLiveDate());
        target.setCapacityPoints(source.getCapacityPoints());
        target.setCommittedPoints(source.getCommittedPoints());
        target.setReadiness(source.getReadiness());
        target.setBlockedItems(source.getBlockedItems());
        target.setScope(source.getScope());
        target.setRisk(source.getRisk());
        target.setDecision(source.getDecision());
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
