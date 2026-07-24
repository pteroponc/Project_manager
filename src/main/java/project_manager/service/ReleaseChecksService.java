package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReleaseChecksService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final String INTEGRATION_STEP = "Run integration tests and package the application";
    private static final String SMOKE_STEP = "Smoke test packaged JAR";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String repository;

    private Instant cachedAt = Instant.EPOCH;
    private ReleaseChecksSnapshot cachedSnapshot;

    public ReleaseChecksService(
        ObjectMapper objectMapper,
        @Value("${release-checks.github-repository:pteroponc/Project_manager}") String repository
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public ReleaseChecksSnapshot getSnapshot() {
        return getSnapshot(false);
    }

    public synchronized ReleaseChecksSnapshot getSnapshot(boolean forceRefresh) {
        if (!forceRefresh && cachedSnapshot != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return cachedSnapshot;
        }

        cachedSnapshot = fetchSnapshot();
        cachedAt = Instant.now();
        return cachedSnapshot;
    }

    private ReleaseChecksSnapshot fetchSnapshot() {
        try {
            JsonNode runs = getJson("/actions/runs?per_page=30").path("workflow_runs");
            JsonNode ciRun = latestRun(runs, "CI");
            JsonNode releaseRun = latestRun(runs, "Release");
            ReleaseInfo latestRelease = fetchLatestRelease();

            List<ReleaseCheck> checks = new ArrayList<>();
            checks.add(workflowCheck(
                "Интеграционные API-тесты",
                "mvn clean verify",
                ciRun,
                INTEGRATION_STEP
            ));
            checks.add(workflowCheck(
                "Smoke-тест JAR",
                "Запуск JAR и /api/health",
                ciRun,
                SMOKE_STEP
            ));
            checks.add(workflowCheck(
                "Публикация релиза",
                latestRelease == null ? "Релиз еще не опубликован" : latestRelease.tagName(),
                releaseRun,
                null
            ));

            return new ReleaseChecksSnapshot(
                repository,
                true,
                Instant.now().toString(),
                "Данные GitHub обновлены.",
                latestRelease,
                checks
            );
        } catch (Exception exception) {
            return new ReleaseChecksSnapshot(
                repository,
                false,
                Instant.now().toString(),
                "Не удалось получить данные GitHub. Проверьте сетевое подключение и повторите попытку.",
                null,
                List.of()
            );
        }
    }

    private ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        HttpResponse<String> response = send("/releases/latest");
        if (response.statusCode() == 404) {
            return null;
        }
        requireSuccess(response);

        JsonNode release = objectMapper.readTree(response.body());
        int assetCount = release.path("assets").isArray() ? release.path("assets").size() : 0;
        return new ReleaseInfo(
            release.path("tag_name").asText(""),
            release.path("name").asText(release.path("tag_name").asText("")),
            release.path("published_at").asText(""),
            release.path("html_url").asText(""),
            assetCount
        );
    }

    private ReleaseCheck workflowCheck(String title, String description, JsonNode run, String stepName)
        throws IOException, InterruptedException {
        if (run == null || run.isMissingNode()) {
            return new ReleaseCheck(title, description, "not_run", "", "");
        }

        String state = stepName == null ? stateOf(run) : stepState(run, stepName);
        return new ReleaseCheck(
            title,
            description,
            state,
            run.path("html_url").asText(""),
            run.path("updated_at").asText("")
        );
    }

    private String stepState(JsonNode run, String stepName) throws IOException, InterruptedException {
        String runId = run.path("id").asText();
        if (runId.isBlank()) {
            return stateOf(run);
        }

        JsonNode jobs = getJson("/actions/runs/" + runId + "/jobs?per_page=50").path("jobs");
        for (JsonNode job : jobs) {
            for (JsonNode step : job.path("steps")) {
                if (stepName.equals(step.path("name").asText())) {
                    return stateOf(step);
                }
            }
        }
        return stateOf(run);
    }

    private JsonNode latestRun(JsonNode runs, String workflowName) {
        if (!runs.isArray()) {
            return null;
        }

        for (JsonNode run : runs) {
            if (workflowName.equals(run.path("name").asText())) {
                return run;
            }
        }
        return null;
    }

    private String stateOf(JsonNode node) {
        String conclusion = node.path("conclusion").asText("");
        if (!conclusion.isBlank() && !"null".equals(conclusion)) {
            return conclusion;
        }

        String status = node.path("status").asText("");
        return status.isBlank() || "null".equals(status) ? "unknown" : status;
    }

    private JsonNode getJson(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = send(path);
        requireSuccess(response);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> send(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/" + repository + path))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Project-Manager-release-dashboard")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void requireSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API returned " + response.statusCode());
        }
    }

    public record ReleaseChecksSnapshot(
        String repository,
        boolean available,
        String updatedAt,
        String message,
        ReleaseInfo latestRelease,
        List<ReleaseCheck> checks
    ) {
    }

    public record ReleaseInfo(
        String tagName,
        String name,
        String publishedAt,
        String url,
        int assetCount
    ) {
    }

    public record ReleaseCheck(
        String title,
        String description,
        String state,
        String url,
        String updatedAt
    ) {
    }
}
