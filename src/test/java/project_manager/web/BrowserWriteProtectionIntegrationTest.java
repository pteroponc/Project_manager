package project_manager.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pmtest_browser_writes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class BrowserWriteProtectionIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired BrowserWriteProtectionFilter protection;
    @Autowired ObjectMapper json;
    private MockMvc browser;

    @BeforeEach
    void rawClientWithoutDefaultHeaders() {
        browser = MockMvcBuilders.webAppContextSetup(context).addFilters(protection).build();
    }

    @Test
    void rejectsMissingWrongAndCrossOriginMarkersWithoutBlockingReads() throws Exception {
        browser.perform(post("/api/board/personal/tasks")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("WRITE_REQUEST_FORBIDDEN"));
        browser.perform(put("/api/board/personal/tasks/example")).andExpect(status().isForbidden());
        browser.perform(patch("/api/projects/example")).andExpect(status().isForbidden());
        browser.perform(delete("/api/board/personal/tasks/example")).andExpect(status().isForbidden());
        browser.perform(delete("/api/projects/example/board/cards/example")).andExpect(status().isForbidden());
        browser.perform(post("/api/board/personal/tasks").header("X-PM-Write", "invalid"))
            .andExpect(status().isForbidden());
        browser.perform(post("/api/board/personal/tasks").header("X-PM-Write", "1").header("Sec-Fetch-Site", "cross-site"))
            .andExpect(status().isForbidden());
        browser.perform(patch("/api/projects/example").header("X-PM-Write", "1").header("Sec-Fetch-Site", "same-site"))
            .andExpect(status().isForbidden());
        browser.perform(get("/api/board/personal")).andExpect(status().isOk());
        browser.perform(get("/api/my-day")).andExpect(status().isOk());
    }

    @Test
    void foreignOriginCannotPreflightTheWriteHeader() throws Exception {
        browser.perform(options("/api/board/personal/tasks")
                .header(HttpHeaders.ORIGIN, "https://foreign.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-pm-write, content-type"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void supportsTaskLifecycleAndExistingProjectWritesWithTheMarker() throws Exception {
        String projectId = json.readTree(browser.perform(post("/api/projects").header("X-PM-Write", "1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"Synthetic project","owner":"Demo","status":"active","health":"green",
                     "deliveryModel":"kanban","quarter":"Q4","deadline":"2026-12-31","milestone":"Plan",
                     "risk":"Example","dependency":"Example","kpiName":"Example","kpiTarget":"Example","summary":"Demo"}
                    """))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
        browser.perform(patch("/api/projects/{id}", projectId).header("X-PM-Write", "1").header("Sec-Fetch-Site", "same-origin")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"expectedVersion":0,"owner":"Synthetic participant"}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.owner").value("Synthetic participant"));
        String taskId = json.readTree(browser.perform(post("/api/board/personal/tasks")
                .header("X-PM-Write", "1").header("Sec-Fetch-Site", "same-origin")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"title":"Synthetic task","columnKey":"backlog","priority":"high"}
                    """))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        browser.perform(put("/api/board/personal/tasks/{id}", taskId).header("X-PM-Write", "1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"title":"Synthetic task updated","columnKey":"ready","priority":"high","expectedVersion":0}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        browser.perform(delete("/api/board/personal/tasks/{id}", taskId).param("expectedVersion", "1")
                .header("X-PM-Write", "1").header("Sec-Fetch-Site", "same-origin"))
            .andExpect(status().isNoContent());
    }
}
