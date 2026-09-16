package project_manager.service;

import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectRepository;
import project_manager.web.dto.ProjectRequest;
import project_manager.web.dto.ProjectResponse;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<ProjectResponse> getProjects(String quarter, String health) {
        return findProjects(quarter, health).stream()
            .map(this::toResponse)
            .toList();
    }

    public ProjectResponse getProject(String id) {
        return toResponse(requireProject(id));
    }

    public ProjectResponse createProject(ProjectRequest request) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(UUID.randomUUID().toString());
        applyRequest(entity, request);
        return toResponse(projectRepository.save(entity));
    }

    public ProjectResponse updateProject(String id, ProjectRequest request) {
        ProjectEntity entity = requireProject(id);
        applyRequest(entity, request);
        return toResponse(projectRepository.save(entity));
    }

    public void deleteProject(String id) {
        projectRepository.delete(requireProject(id));
    }

    public List<ProjectEntity> findProjects(String quarter, String health) {
        boolean allQuarter = "all".equalsIgnoreCase(quarter);
        boolean allHealth = "all".equalsIgnoreCase(health);

        if (allQuarter && allHealth) {
            return projectRepository.findAll();
        }
        if (allQuarter) {
            return projectRepository.findByHealthIgnoreCase(health);
        }
        if (allHealth) {
            return projectRepository.findByQuarterIgnoreCase(quarter);
        }
        return projectRepository.findByQuarterIgnoreCaseAndHealthIgnoreCase(quarter, health);
    }

    public ProjectEntity requireProject(String id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
    }

    private void applyRequest(ProjectEntity entity, ProjectRequest request) {
        entity.setName(request.name());
        entity.setOwner(request.owner());
        entity.setStatus(request.status());
        entity.setHealth(request.health());
        entity.setDeliveryModel(normalizeDeliveryModel(request.deliveryModel()));
        entity.setBudget(request.budget());
        entity.setProgress(request.progress());
        entity.setQuarter(request.quarter());
        entity.setDeadline(request.deadline());
        entity.setMilestone(request.milestone());
        entity.setRisk(request.risk());
        entity.setDependency(request.dependency());
        entity.setKpiName(request.kpiName());
        entity.setKpiTarget(request.kpiTarget());
        entity.setSummary(request.summary());
    }

    private ProjectResponse toResponse(ProjectEntity entity) {
        return new ProjectResponse(
            entity.getId(),
            entity.getName(),
            entity.getOwner(),
            entity.getStatus(),
            entity.getHealth(),
            normalizeDeliveryModel(entity.getDeliveryModel()),
            entity.getBudget(),
            entity.getProgress(),
            entity.getQuarter(),
            entity.getDeadline(),
            entity.getMilestone(),
            entity.getRisk(),
            entity.getDependency(),
            entity.getKpiName(),
            entity.getKpiTarget(),
            entity.getSummary()
        );
    }

    private String normalizeDeliveryModel(String deliveryModel) {
        if (deliveryModel == null || deliveryModel.isBlank()) {
            return "kanban";
        }
        return switch (deliveryModel.trim().toLowerCase()) {
            case "kanban", "scrum", "waterfall" -> deliveryModel.trim().toLowerCase();
            default -> throw new IllegalArgumentException("Unsupported delivery model: " + deliveryModel);
        };
    }
}
