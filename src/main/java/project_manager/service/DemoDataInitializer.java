package project_manager.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project_manager.repository.*;
import project_manager.web.dto.BoardCardRequest;

@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {
    private final ProjectRepository projects;
    private final ReleaseTrainRepository trains;
    private final BoardCardRepository cards;
    private final BoardService boards;

    public DemoDataInitializer(ProjectRepository projects, ReleaseTrainRepository trains,
                               BoardCardRepository cards, BoardService boards) {
        this.projects = projects;
        this.trains = trains;
        this.cards = cards;
        this.boards = boards;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Never repair, refill or overwrite an existing dataset, even in demo mode.
        if (projects.count() != 0 || trains.count() != 0 || cards.count() != 0) return;
        var examples = projects.saveAll(DemoFixtures.demoProjects());
        trains.saveAll(DemoFixtures.demoTrains());
        for (var project : examples) {
            String column = switch (project.getDeliveryModel()) {
                case "scrum" -> "product-backlog";
                case "waterfall" -> "initiation";
                default -> "backlog";
            };
            boards.createCard(project.getId(), new BoardCardRequest(
                "Демонстрационная задача", project.getSummary(), project.getOwner(),
                project.getDeadline(), column, "medium", "demo", 3, false), project.getDeliveryModel());
        }
    }
}
