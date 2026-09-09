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

class AnalyticsControllerTest extends AbstractIntegrationTest {

    private String token;
    private long deckId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser("owner@example.com");
        deckId = createDeck("Biology");
    }

    @Test
    void emptyAccountHasZeroSummary() throws Exception {
        mockMvc.perform(get("/api/analytics/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueToday").value(0))
                .andExpect(jsonPath("$.reviewedToday").value(0))
                .andExpect(jsonPath("$.totalCards").value(0))
                .andExpect(jsonPath("$.cardsMastered").value(0))
                .andExpect(jsonPath("$.masteredPercent").value(0))
                .andExpect(jsonPath("$.totalDecks").value(1))
                .andExpect(jsonPath("$.totalReviews").value(0))
                .andExpect(jsonPath("$.averageRecall").doesNotExist())
                .andExpect(jsonPath("$.retentionRate").doesNotExist())
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.quizzesTaken").value(0))
                .andExpect(jsonPath("$.averageQuizPercent").doesNotExist());

        mockMvc.perform(get("/api/analytics/topics").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void summaryReflectsReviewsMasteryAndStreak() throws Exception {
        long mastered = createCard(deckId, "mastered", "Cells");
        long weak = createCard(deckId, "weak", "Genetics");
        createCard(deckId, "untouched", "Genetics");
        grade(mastered, 4);
        grade(mastered, 4);
        grade(mastered, 4);
        grade(mastered, 4); // interval 38 -> mastered
        grade(weak, 1);

        mockMvc.perform(get("/api/analytics/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalCards").value(3))
                .andExpect(jsonPath("$.cardsMastered").value(1))
                .andExpect(jsonPath("$.masteredPercent").value(33))
                .andExpect(jsonPath("$.dueToday").value(1))
                .andExpect(jsonPath("$.reviewedToday").value(5))
                .andExpect(jsonPath("$.totalReviews").value(5))
                .andExpect(jsonPath("$.averageRecall").value(3.4))
                .andExpect(jsonPath("$.retentionRate").value(80))
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(1))
                .andExpect(jsonPath("$.lastActiveDate").value(LocalDate.now().toString()));
    }

    @Test
    void activitySeriesCoversEveryDayAndFillsGaps() throws Exception {
        long card = createCard(deckId, "q", "Cells");
        grade(card, 5);
        grade(card, 2);
        backdateReview(card, 2, 4);
        backdateReview(card, 2, 0);

        mockMvc.perform(get("/api/analytics/activity").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("days", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].date").value(LocalDate.now().minusDays(4).toString()))
                .andExpect(jsonPath("$[0].reviews").value(0))
                .andExpect(jsonPath("$[0].averageQuality").doesNotExist())
                .andExpect(jsonPath("$[0].retentionPercent").doesNotExist())
                .andExpect(jsonPath("$[2].date").value(LocalDate.now().minusDays(2).toString()))
                .andExpect(jsonPath("$[2].reviews").value(2))
                .andExpect(jsonPath("$[2].successful").value(1))
                .andExpect(jsonPath("$[2].averageQuality").value(2.0))
                .andExpect(jsonPath("$[2].retentionPercent").value(50))
                .andExpect(jsonPath("$[4].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[4].reviews").value(2))
                .andExpect(jsonPath("$[4].averageQuality").value(3.5));

        mockMvc.perform(get("/api/analytics/activity").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("days", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void masterySeriesIsCumulativeByFirstMasteredDay() throws Exception {
        long early = createCard(deckId, "early", "Cells");
        long today = createCard(deckId, "today", "Cells");
        // "early" reached a 21-day interval three days ago; "today" reaches it now.
        backdateReviewWithInterval(early, 3, 21);
        for (int i = 0; i < 4; i++) {
            grade(today, 4);
        }

        mockMvc.perform(get("/api/analytics/mastery").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("days", "4"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].date").value(LocalDate.now().minusDays(3).toString()))
                .andExpect(jsonPath("$[0].masteredCards").value(1))
                .andExpect(jsonPath("$[1].masteredCards").value(1))
                .andExpect(jsonPath("$[2].masteredCards").value(1))
                .andExpect(jsonPath("$[3].masteredCards").value(2));

        // A window that starts after "early" was mastered still counts it in the running total.
        mockMvc.perform(get("/api/analytics/mastery").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("days", "1"))
                .andExpect(jsonPath("$[0].masteredCards").value(2));
    }

    @Test
    void weakTopicsFollowTheDeterministicRule() throws Exception {
        long genetics1 = createCard(deckId, "g1", "Genetics");
        long genetics2 = createCard(deckId, "g2", " genetics ");
        long cells = createCard(deckId, "c1", "Cells");
        long thin = createCard(deckId, "t1", "Thin");
        long improved = createCard(deckId, "i1", "Improved");
        createCard(deckId, "no topic", null);

        // Genetics: three poor reviews across two cards (grouped case-insensitively) -> weak.
        grade(genetics1, 1);
        grade(genetics2, 2);
        grade(genetics1, 0);
        // Cells: consistently good -> not weak.
        grade(cells, 5);
        grade(cells, 4);
        grade(cells, 4);
        // Thin: poor but only two reviews -> not enough history.
        grade(thin, 0);
        grade(thin, 1);
        // Improved: three failures long ago, then ten good recent reviews -> recovered.
        for (int i = 0; i < 3; i++) {
            backdateReview(improved, 30, 0);
        }
        for (int i = 0; i < 10; i++) {
            grade(improved, 4);
        }

        mockMvc.perform(get("/api/analytics/topics").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].topic").value(org.hamcrest.Matchers.equalToIgnoringCase("genetics")))
                .andExpect(jsonPath("$[0].cardCount").value(2))
                .andExpect(jsonPath("$[0].reviews").value(3))
                .andExpect(jsonPath("$[0].averageQuality").value(1.0))
                .andExpect(jsonPath("$[0].recentAverageQuality").value(1.0))
                .andExpect(jsonPath("$[0].successRatePercent").value(0))
                .andExpect(jsonPath("$[0].weak").value(true))
                .andExpect(jsonPath("$[0].lastReviewedAt").isNotEmpty())
                .andExpect(jsonPath("$[?(@.topic == 'Thin')].weak").value(false))
                .andExpect(jsonPath("$[?(@.topic == 'Cells')].weak").value(false))
                .andExpect(jsonPath("$[?(@.topic == 'Cells')].successRatePercent").value(100))
                .andExpect(jsonPath("$[?(@.topic == 'Improved')].weak").value(false))
                .andExpect(jsonPath("$[?(@.topic == 'Improved')].reviews").value(13))
                .andExpect(jsonPath("$[?(@.topic == 'Improved')].recentAverageQuality").value(4.0))
                .andExpect(jsonPath("$[?(@.topic == 'Improved')].averageQuality").value(3.08));

        mockMvc.perform(get("/api/analytics/weak-topics").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].topic").value(org.hamcrest.Matchers.equalToIgnoringCase("genetics")));
    }

    @Test
    void topicsCanBeScopedToADeckAndAreIsolatedBetweenUsers() throws Exception {
        long otherDeck = createDeck("Chemistry");
        long bio = createCard(deckId, "b", "Cells");
        long chem = createCard(otherDeck, "c", "Bonds");
        grade(bio, 1);
        grade(chem, 5);

        mockMvc.perform(get("/api/analytics/topics").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("deckId", String.valueOf(otherDeck)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].topic").value("Bonds"));

        String intruder = registerUser("intruder@example.com");
        mockMvc.perform(get("/api/analytics/topics").header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .param("deckId", String.valueOf(otherDeck)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/analytics/topics").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/analytics/summary").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(jsonPath("$.totalReviews").value(0));
        mockMvc.perform(get("/api/analytics/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void quizStatisticsAppearInTheSummary() throws Exception {
        jdbcTemplate.update("INSERT INTO quizzes (deck_id, title) VALUES (?, 'Q')", deckId);
        Long quizId = jdbcTemplate.queryForObject("SELECT max(id) FROM quizzes", Long.class);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'owner@example.com'", Long.class);
        jdbcTemplate.update("INSERT INTO quiz_attempts (quiz_id, user_id, score, total_questions) VALUES (?, ?, 3, 4)",
                quizId, userId);
        jdbcTemplate.update("INSERT INTO quiz_attempts (quiz_id, user_id, score, total_questions) VALUES (?, ?, 1, 4)",
                quizId, userId);

        mockMvc.perform(get("/api/analytics/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.quizzesTaken").value(2))
                .andExpect(jsonPath("$.averageQuizPercent").value(50));
    }

    private void grade(long cardId, int quality) throws Exception {
        mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": " + quality + "}"))
                .andExpect(status().isOk());
    }

    private void backdateReview(long cardId, int daysAgo, int quality) {
        backdateReview(cardId, daysAgo, quality, 1);
    }

    private void backdateReviewWithInterval(long cardId, int daysAgo, int newInterval) {
        backdateReview(cardId, daysAgo, 4, newInterval);
    }

    private void backdateReview(long cardId, int daysAgo, int quality, int newInterval) {
        jdbcTemplate.update("""
                INSERT INTO review_history (card_id, user_id, quality_score, previous_interval, new_interval,
                                            previous_ease_factor, new_ease_factor, reviewed_at)
                SELECT ?, d.user_id, ?, 0, ?, 2.50, 2.50, now() - make_interval(days => ?)
                FROM cards c JOIN decks d ON d.id = c.deck_id WHERE c.id = ?
                """, cardId, quality, newInterval, daysAgo, cardId);
    }

    private long createDeck(String name) throws Exception {
        return json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long createCard(long deck, String question, String topic) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("question", question);
            put("answer", "A");
            put("topic", topic);
        }});
        return json(mockMvc.perform(post("/api/decks/" + deck + "/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
}
