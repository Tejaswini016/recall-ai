package com.recallai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Phase 6 of the adaptive upgrade: the readiness estimate and the upcoming-review outlook over the API. */
class ReadinessControllerTest extends AbstractIntegrationTest {

    private String token;
    private long deckId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser("owner@example.com");
        deckId = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Biology\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void readinessIsAnExplainedEstimateBuiltFromRealActivity() throws Exception {
        mockMvc.perform(get("/api/analytics/readiness").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.estimate").value(true))
                .andExpect(jsonPath("$.confidence").value("LOW"))
                .andExpect(jsonPath("$.components.length()").value(5));

        long cells = createCard("c", "Cells");
        long genetics = createCard("g", "Genetics");
        for (int i = 0; i < 3; i++) {
            grade(cells, 5);
            grade(genetics, 4);
        }
        mockMvc.perform(get("/api/analytics/readiness").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.greaterThan(80)))
                .andExpect(jsonPath("$.label").value("Exam ready"))
                .andExpect(jsonPath("$.evidenceAttempts").value(6))
                .andExpect(jsonPath("$.components[0].key").value("accuracy"))
                .andExpect(jsonPath("$.components[0].score").value(100))
                .andExpect(jsonPath("$.components[1].key").value("retention"))
                .andExpect(jsonPath("$.components[1].available").value(true))
                .andExpect(jsonPath("$.components[2].detail").value(org.hamcrest.Matchers.containsString("2 of 2 topics")))
                .andExpect(jsonPath("$.components[3].detail").value(org.hamcrest.Matchers.containsString("1 of the last 7 days")))
                .andExpect(jsonPath("$.components[4].available").value(false))
                .andExpect(jsonPath("$.recommendation").value(org.hamcrest.Matchers.containsString("mock exam")));
        mockMvc.perform(get("/api/analytics/readiness")).andExpect(status().isUnauthorized());
    }

    @Test
    void upcomingReviewsCountCardsPerDayAndTheOverdueBacklog() throws Exception {
        long overdue = createCard("overdue", null);
        long today = createCard("today", null);
        long inTwoDays = createCard("later", null);
        createCard("far", null);
        LocalDate now = LocalDate.now();
        jdbcTemplate.update("UPDATE cards SET due_date = ? WHERE id = ?", now.minusDays(3), overdue);
        jdbcTemplate.update("UPDATE cards SET due_date = ? WHERE id = ?", now, today);
        jdbcTemplate.update("UPDATE cards SET due_date = ? WHERE id = ?", now.plusDays(2), inTwoDays);
        jdbcTemplate.update("UPDATE cards SET due_date = ? WHERE question = 'far'", now.plusDays(30));

        mockMvc.perform(get("/api/reviews/upcoming?days=7").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(now.toString()))
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.dueToday").value(1))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].date").value(now.toString()))
                .andExpect(jsonPath("$.days[0].cards").value(1))
                .andExpect(jsonPath("$.days[1].cards").value(0))
                .andExpect(jsonPath("$.days[2].cards").value(1))
                .andExpect(jsonPath("$.days[6].cards").value(0));
        mockMvc.perform(get("/api/reviews/upcoming?days=0").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    private void grade(long cardId, int quality) throws Exception {
        mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": " + quality + "}"))
                .andExpect(status().isOk());
    }

    private long createCard(String question, String topic) throws Exception {
        String body = topic == null
                ? "{\"question\":\"" + question + "\",\"answer\":\"A\"}"
                : "{\"question\":\"" + question + "\",\"answer\":\"A\",\"topic\":\"" + topic + "\"}";
        return json(mockMvc.perform(post("/api/decks/" + deckId + "/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
}
