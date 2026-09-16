package project_manager.service;

import project_manager.domain.ProjectEntity;
import project_manager.domain.ReleaseTrainEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Only used by the explicitly enabled, isolated demo initializer. */
final class DemoFixtures {
    static List<ProjectEntity> demoProjects() {
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

    private static ProjectEntity buildProject(
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

    private static String normalizeDeliveryModel(String deliveryModel) {
        if (deliveryModel == null || deliveryModel.isBlank()) {
            return "kanban";
        }
        return switch (deliveryModel.trim().toLowerCase()) {
            case "kanban", "scrum", "waterfall" -> deliveryModel.trim().toLowerCase();
            default -> throw new IllegalArgumentException("Unsupported delivery model: " + deliveryModel);
        };
    }
    static List<ReleaseTrainEntity> demoTrains() {
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

    private static ReleaseTrainEntity buildTrain(
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

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "planning";
        }
        return switch (status.trim().toLowerCase()) {
            case "planning", "boarding", "frozen", "qa", "released" -> status.trim().toLowerCase();
            default -> throw new IllegalArgumentException("Unsupported release train status: " + status);
        };
    }
}
