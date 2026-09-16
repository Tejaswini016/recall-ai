package com.recallai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.recallai.AbstractIntegrationTest;
import com.recallai.ai.AiUnavailableException;
import com.recallai.ai.FakeClaudeClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Phase 4 of the adaptive upgrade: study plans built from performance, with model-written advice. */
@TestPropertySource(properties = "recallai.ai.api-key=test-key")
class StudyPlanControllerTest extends AbstractIntegrationTest {

    private static final String ADVICE = """
            {"summary": "Three weeks, genetics first.",
             "topicAdvice": [{"topic": "Genetics", "advice": "Relearn replication before quizzing."},
                             {"topic": "Cells", "advice": "Light maintenance."},
                             {"topic": "Not in plan", "advice": "dropped"}]}
            """;

    @Autowired
    private FakeClaudeClient fakeClaudeClient;

    private String token;
    private long deckId;

    @BeforeEach
    void setUp() throws Exception {
        fakeClaudeClient.reset();
        token = registerUser("owner@example.com");
        deckId = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Biology\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void planIsScheduledFromPerformanceAndExplainedByTheModel() throws Exception {
        long genetics = createCard("g", "Genetics");
        long cells = createCard("c", "Cells");
        for (int i = 0; i < 3; i++) {
            grade(genetics, 1);
            grade(cells, 5);
        }
        fakeClaudeClient.reply(ADVICE);
        LocalDate exam = LocalDate.now().plusDays(21);

        JsonNode plan = json(mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examName\": \" Biology final \", \"examDate\": \"" + exam + "\","
                                + " \"topics\": [\"Genetics\", \"Cells\", \" Genetics \"], \"knowledgeLevel\": \"INTERMEDIATE\","
                                + " \"minutesPerDay\": 60, \"preferredDays\": [1,2,3,4,5,6,7]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.examName").value("Biology final"))
                .andExpect(jsonPath("$.topics").value(org.hamcrest.Matchers.contains("Genetics", "Cells")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.aiGenerated").value(true))
                .andExpect(jsonPath("$.summary").value("Three weeks, genetics first."))
                .andExpect(jsonPath("$.topicAdvice.length()").value(2))
                .andExpect(jsonPath("$.topicAdvice[0].topic").value("Genetics"))
                .andExpect(jsonPath("$.progress.totalTasks").isNumber())
                .andExpect(jsonPath("$.progress.doneTasks").value(0))
                .andExpect(jsonPath("$.progress.daysUntilExam").value(21))
                .andExpect(jsonPath("$.progress.studyDaysTotal").value(21))
                .andExpect(jsonPath("$.progress.onTrack").value(true))
                .andExpect(jsonPath("$.tasks[0].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.tasks[0].type").value("REVIEW_DUE"))
                .andReturn());

        String prompt = fakeClaudeClient.prompts().get(0).messages().get(0).content();
        assertThat(prompt).contains("Exam: Biology final", "Days until exam: 21", "Genetics | standing: CRITICAL | accuracy: 0% over 3 attempts",
                "Cells | standing: STRONG | accuracy: 100% over 3 attempts");

        int geneticsMinutes = 0;
        int cellsMinutes = 0;
        for (JsonNode task : plan.get("tasks")) {
            if ("Genetics".equals(task.path("topic").asText(null))) {
                geneticsMinutes += task.get("minutes").asInt();
            } else if ("Cells".equals(task.path("topic").asText(null))) {
                cellsMinutes += task.get("minutes").asInt();
            }
        }
        assertThat(geneticsMinutes).isGreaterThan(cellsMinutes);

        long planId = plan.get("id").asLong();
        mockMvc.perform(get("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(planId))
                .andExpect(jsonPath("$[0].progress.totalTasks").value(plan.get("tasks").size()));
        mockMvc.perform(get("/api/study-plans/today").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.activePlans").value(1))
                .andExpect(jsonPath("$.tasks.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.tasks[0].examName").value("Biology final"))
                .andExpect(jsonPath("$.totalMinutes").value(60))
                .andExpect(jsonPath("$.doneMinutes").value(0))
                .andExpect(jsonPath("$.carriedOver.length()").value(0));
    }

    @Test
    void withoutTheModelThePlanStillExistsWithADeterministicSummary() throws Exception {
        fakeClaudeClient.fail(new AiUnavailableException("down", false));
        mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(LocalDate.now().plusDays(7), 30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("7 study days until Biology final")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("no results yet")))
                .andExpect(jsonPath("$.topicAdvice.length()").value(0))
                .andExpect(jsonPath("$.tasks.length()").value(org.hamcrest.Matchers.greaterThan(6)));
    }

    @Test
    void tasksCanBeCompletedAndThePlanFinishesWhenNothingIsPending() throws Exception {
        fakeClaudeClient.fail(new AiUnavailableException("down", false));
        JsonNode plan = json(mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(planBody(LocalDate.now().plusDays(2), 20)))
                .andExpect(status().isCreated()).andReturn());
        long planId = plan.get("id").asLong();
        List<Long> taskIds = new ArrayList<>();
        for (JsonNode task : plan.get("tasks")) {
            taskIds.add(task.get("id").asLong());
        }
        assertThat(taskIds).hasSizeGreaterThanOrEqualTo(2);

        mockMvc.perform(patch("/api/study-plans/" + planId + "/tasks/" + taskIds.get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        mockMvc.perform(get("/api/study-plans/" + planId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.progress.doneTasks").value(1))
                .andExpect(jsonPath("$.progress.doneMinutes").value(plan.get("tasks").get(0).get("minutes").asInt()));
        mockMvc.perform(get("/api/study-plans/today").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.doneMinutes").value(plan.get("tasks").get(0).get("minutes").asInt()));

        for (int i = 1; i < taskIds.size(); i++) {
            mockMvc.perform(patch("/api/study-plans/" + planId + "/tasks/" + taskIds.get(i))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SKIPPED\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/study-plans/" + planId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress.percentComplete").value(100));

        // Reopening a task reactivates the plan; explicit status changes work too.
        mockMvc.perform(patch("/api/study-plans/" + planId + "/tasks/" + taskIds.get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"PENDING\"}"));
        mockMvc.perform(get("/api/study-plans/" + planId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(patch("/api/study-plans/" + planId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ARCHIVED\"}"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/study-plans/today").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.activePlans").value(0))
                .andExpect(jsonPath("$.tasks.length()").value(0));
    }

    @Test
    void regenerationKeepsDoneWorkAndRebalancesFromNewResults() throws Exception {
        fakeClaudeClient.fail(new AiUnavailableException("down", false));
        JsonNode plan = json(mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(planBody(LocalDate.now().plusDays(10), 60)))
                .andExpect(status().isCreated()).andReturn());
        long planId = plan.get("id").asLong();
        long firstTask = plan.get("tasks").get(0).get("id").asLong();
        mockMvc.perform(patch("/api/study-plans/" + planId + "/tasks/" + firstTask)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DONE\"}"));

        // Cells becomes critical; regeneration should now favour it.
        long cells = createCard("c", "Cells");
        for (int i = 0; i < 4; i++) {
            grade(cells, 0);
        }
        fakeClaudeClient.reply(ADVICE);
        JsonNode regenerated = json(mockMvc.perform(post("/api/study-plans/" + planId + "/regenerate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiGenerated").value(true))
                .andExpect(jsonPath("$.progress.doneTasks").value(1))
                .andReturn());
        assertThat(regenerated.get("tasks").get(0).get("id").asLong()).isEqualTo(firstTask);
        assertThat(regenerated.get("tasks").get(0).get("status").asText()).isEqualTo("DONE");
        int cellsMinutes = 0;
        int geneticsMinutes = 0;
        for (JsonNode task : regenerated.get("tasks")) {
            if ("Cells".equals(task.path("topic").asText(null))) {
                cellsMinutes += task.get("minutes").asInt();
            } else if ("Genetics".equals(task.path("topic").asText(null))) {
                geneticsMinutes += task.get("minutes").asInt();
            }
        }
        assertThat(cellsMinutes).isGreaterThan(geneticsMinutes);
        assertThat(fakeClaudeClient.prompts().get(1).messages().get(0).content()).contains("Cells | standing: CRITICAL");
    }

    @Test
    void validationAndOwnership() throws Exception {
        mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(planBody(LocalDate.now(), 30)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examName\": \"X\", \"examDate\": \"" + LocalDate.now().plusDays(5) + "\", \"topics\": [],"
                                + " \"knowledgeLevel\": \"BEGINNER\", \"minutesPerDay\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.topics").exists())
                .andExpect(jsonPath("$.fieldErrors.minutesPerDay").exists());
        assertThat(fakeClaudeClient.calls()).isZero();

        fakeClaudeClient.fail(new AiUnavailableException("down", false));
        long planId = json(mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(planBody(LocalDate.now().plusDays(3), 30)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        String stranger = registerUser("stranger@example.com");
        mockMvc.perform(get("/api/study-plans/" + planId).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/study-plans/" + planId + "/regenerate").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/study-plans")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/study-plans/" + planId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/study-plans").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private static String planBody(LocalDate exam, int minutes) {
        return "{\"examName\": \"Biology final\", \"examDate\": \"" + exam + "\", \"topics\": [\"Genetics\", \"Cells\"],"
                + " \"knowledgeLevel\": \"BEGINNER\", \"minutesPerDay\": " + minutes + "}";
    }

    private void grade(long cardId, int quality) throws Exception {
        mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": " + quality + "}"))
                .andExpect(status().isOk());
    }

    private long createCard(String question, String topic) throws Exception {
        return json(mockMvc.perform(post("/api/decks/" + deckId + "/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"answer\":\"A\",\"topic\":\"" + topic + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
}
