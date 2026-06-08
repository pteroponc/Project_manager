package project_manager.service;

import project_manager.domain.ProjectEntity;
import project_manager.repository.ProjectRepository;
import project_manager.web.dto.ProjectRequest;
import project_manager.web.dto.ProjectResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostConstruct
    void seed() {
        List<ProjectEntity> demoProjects = demoProjects();
        if (projectRepository.count() > 0) {
            refreshDemoProjects(demoProjects);
            return;
        }

        projectRepository.saveAll(demoProjects);
    }

    private List<ProjectEntity> demoProjects() {
        return List.of(
            buildProject(
            "Digital Commerce Platform",
            "Polina",
            "active",
            "yellow",
            "kanban",
            5_200_000,
            63,
            "Q2 2026",
            LocalDate.now().plusDays(45).toString(),
            "Бета-запуск кабинета партнера",
            "Интеграция с ERP не укладывается в окно релиза",
            "Готовность ERP API и команды интеграции",
            "Конверсия в заказ",
            "+12% к концу квартала",
            "Перестройка цифровой витрины и процессов заказа для роста онлайн-выручки."
        ),
            buildProject(
            "Knowledge Hub Migration",
            "Architecture Office",
            "planned",
            "green",
            "waterfall",
            1_800_000,
            22,
            "Q3 2026",
            LocalDate.now().plusDays(72).toString(),
            "Согласовать карту миграции документов",
            "Не вся база знаний размечена по владельцам",
            "Назначение владельцев доменов знаний",
            "Доля актуализированной документации",
            "85% страниц с владельцем и датой ревью",
            "Переезд документации и регламентов в единое управляемое хранилище."
        ),
            buildProject(
            "Mobile Workforce Rollout",
            "Delivery Lead",
            "active",
            "green",
            "scrum",
            3_400_000,
            48,
            "Q2 2026",
            LocalDate.now().plusDays(30).toString(),
            "Вывести MVP в конце спринта",
            "Нужна синхронизация с мобильным MDM",
            "Доступ к test-стенду и данным поля",
            "Скорость вывода фичи",
            "2 инкремента за месяц",
            "Запуск мобильного контура для полевых команд с короткими спринтами."
        ));
    }

    private void refreshDemoProjects(List<ProjectEntity> demoProjects) {
        for (ProjectEntity demoProject : demoProjects) {
            projectRepository.findByName(demoProject.getName()).ifPresent(existing -> {
                String id = existing.getId();
                copyProjectFields(demoProject, existing);
                existing.setId(id);
                projectRepository.save(existing);
            });
        }
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

    private ProjectEntity buildProject(
        String name,
        String owner,
        String status,
        String health,
        String deliveryModel,
        int budget,
        int progress,
        String quarter,
        String deadline,
        String milestone,
        String risk,
        String dependency,
        String kpiName,
        String kpiTarget,
        String summary
    ) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name);
        entity.setOwner(owner);
        entity.setStatus(status);
        entity.setHealth(health);
        entity.setDeliveryModel(normalizeDeliveryModel(deliveryModel));
        entity.setBudget(budget);
        entity.setProgress(progress);
        entity.setQuarter(quarter);
        entity.setDeadline(deadline);
        entity.setMilestone(milestone);
        entity.setRisk(risk);
        entity.setDependency(dependency);
        entity.setKpiName(kpiName);
        entity.setKpiTarget(kpiTarget);
        entity.setSummary(summary);
        return entity;
    }

    private void copyProjectFields(ProjectEntity source, ProjectEntity target) {
        target.setName(source.getName());
        target.setOwner(source.getOwner());
        target.setStatus(source.getStatus());
        target.setHealth(source.getHealth());
        target.setDeliveryModel(source.getDeliveryModel());
        target.setBudget(source.getBudget());
        target.setProgress(source.getProgress());
        target.setQuarter(source.getQuarter());
        target.setDeadline(source.getDeadline());
        target.setMilestone(source.getMilestone());
        target.setRisk(source.getRisk());
        target.setDependency(source.getDependency());
        target.setKpiName(source.getKpiName());
        target.setKpiTarget(source.getKpiTarget());
        target.setSummary(source.getSummary());
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
