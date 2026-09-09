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

class ReviewControllerTest extends AbstractIntegrationTest {

    private String token;
    private long deckId;

    @BeforeEach
    void setUpDeck() throws Exception {
        token = registerUser("owner@example.com");
        deckId = createDeck(token, "Biology");
    }

    @Test
    void queueContainsOnlyDueCardsOrderedOverdueThenWeakest() throws Exception {
        long dueToday = createCard(token, deckId, "today");
        long overdueOneDay = createCard(token, deckId, "overdue-1");
        long overdueThreeDays = createCard(token, deckId, "overdue-3");
        long todayButWeak = createCard(token, deckId, "today-weak");
        long future = createCard(token, deckId, "future");
        LocalDate today = LocalDate.now();
        setSchedule(overdueOneDay, today.minusDays(1), "2.50");
        setSchedule(overdueThreeDays, today.minusDays(3), "2.50");
        setSchedule(todayButWeak, today, "1.30");
        setSchedule(future, today.plusDays(1), "1.30");

        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.totalDue").value(4))
                .andExpect(jsonPath("$.cards.length()").value(4))
                .andExpect(jsonPath("$.cards[0].id").value(overdueThreeDays))
                .andExpect(jsonPath("$.cards[0].daysOverdue").value(3))
                .andExpect(jsonPath("$.cards[1].id").value(overdueOneDay))
                .andExpect(jsonPath("$.cards[2].id").value(todayButWeak))
                .andExpect(jsonPath("$.cards[2].daysOverdue").value(0))
                .andExpect(jsonPath("$.cards[3].id").value(dueToday))
                .andExpect(jsonPath("$.cards[3].deckName").value("Biology"));
    }

    @Test
    void queueRespectsLimitButReportsTotal() throws Exception {
        for (int i = 0; i < 3; i++) {
            createCard(token, deckId, "q" + i);
        }

        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("limit", "2"))
                .andExpect(jsonPath("$.totalDue").value(3))
                .andExpect(jsonPath("$.cards.length()").value(2));

        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queueCanBeScopedToADeckTheUserOwns() throws Exception {
        long otherDeck = createDeck(token, "Chemistry");
        createCard(token, deckId, "bio");
        createCard(token, otherDeck, "chem");
        String intruder = registerUser("intruder@example.com");

        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("deckId", String.valueOf(otherDeck)))
                .andExpect(jsonPath("$.totalDue").value(1))
                .andExpect(jsonPath("$.cards[0].question").value("chem"));
        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .param("deckId", String.valueOf(otherDeck)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(jsonPath("$.totalDue").value(0));
    }

    @Test
    void gradingReschedulesCardRecordsHistoryAndRemovesItFromQueue() throws Exception {
        long cardId = createCard(token, deckId, "What is ATP?");
        createCard(token, deckId, "another due card");
        LocalDate today = LocalDate.now();

        mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.successful").value(true))
                .andExpect(jsonPath("$.previousInterval").value(0))
                .andExpect(jsonPath("$.newInterval").value(1))
                .andExpect(jsonPath("$.repetitions").value(1))
                .andExpect(jsonPath("$.previousEaseFactor").value(2.5))
                .andExpect(jsonPath("$.newEaseFactor").value(2.6))
                .andExpect(jsonPath("$.nextDueDate").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.mastered").value(false))
                .andExpect(jsonPath("$.remainingDue").value(1));

        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.interval").value(1))
                .andExpect(jsonPath("$.repetitions").value(1))
                .andExpect(jsonPath("$.easeFactor").value(2.6))
                .andExpect(jsonPath("$.dueDate").value(today.plusDays(1).toString()));

        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalDue").value(1))
                .andExpect(jsonPath("$.cards[0].question").value("another due card"));

        mockMvc.perform(get("/api/reviews/history").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].cardId").value(cardId))
                .andExpect(jsonPath("$.content[0].question").value("What is ATP?"))
                .andExpect(jsonPath("$.content[0].deckName").value("Biology"))
                .andExpect(jsonPath("$.content[0].quality").value(5))
                .andExpect(jsonPath("$.content[0].successful").value(true))
                .andExpect(jsonPath("$.content[0].newInterval").value(1))
                .andExpect(jsonPath("$.content[0].reviewedAt").isNotEmpty());
    }

    @Test
    void repeatedGradingWalksTheSm2LadderAndFailureResets() throws Exception {
        long cardId = createCard(token, deckId, "ladder");

        grade(cardId, 4).andExpect(jsonPath("$.newInterval").value(1));
        grade(cardId, 4).andExpect(jsonPath("$.newInterval").value(6));
        grade(cardId, 4).andExpect(jsonPath("$.newInterval").value(15)).andExpect(jsonPath("$.mastered").value(false));
        grade(cardId, 4).andExpect(jsonPath("$.newInterval").value(38)).andExpect(jsonPath("$.mastered").value(true));
        grade(cardId, 2).andExpect(jsonPath("$.newInterval").value(1))
                .andExpect(jsonPath("$.repetitions").value(0))
                .andExpect(jsonPath("$.successful").value(false))
                .andExpect(jsonPath("$.newEaseFactor").value(2.5));

        mockMvc.perform(get("/api/reviews/history").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].quality").value(2))
                .andExpect(jsonPath("$.content[0].previousInterval").value(38))
                .andExpect(jsonPath("$.content[4].quality").value(4));

        mockMvc.perform(get("/api/decks/" + deckId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.masteredCount").value(0))
                .andExpect(jsonPath("$.dueCount").value(0));
    }

    @Test
    void gradingValidatesQualityRange() throws Exception {
        long cardId = createCard(token, deckId, "q");

        for (String body : new String[] {"{\"quality\": 6}", "{\"quality\": -1}", "{}", "{\"quality\": \"good\"}"}) {
            mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/reviews/history").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void usersCannotGradeEachOthersCards() throws Exception {
        long cardId = createCard(token, deckId, "mine");
        String intruder = registerUser("intruder@example.com");

        mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": 5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.repetitions").value(0));
        mockMvc.perform(get("/api/reviews/history").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void streakReflectsReviewDays() throws Exception {
        mockMvc.perform(get("/api/reviews/streak").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.longestStreak").value(0))
                .andExpect(jsonPath("$.lastActiveDate").doesNotExist())
                .andExpect(jsonPath("$.reviewedToday").value(0));

        long cardId = createCard(token, deckId, "q");
        grade(cardId, 4);
        grade(cardId, 4);
        // Back-date two reviews to make a three-day run ending today, and one isolated review a week ago.
        backdateReview(cardId, 1);
        backdateReview(cardId, 2);
        backdateReview(cardId, 7);

        mockMvc.perform(get("/api/reviews/streak").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.longestStreak").value(3))
                .andExpect(jsonPath("$.lastActiveDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.reviewedToday").value(2));
    }

    @Test
    void reviewEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/reviews/due")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reviews/1").contentType(MediaType.APPLICATION_JSON).content("{\"quality\":4}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reviews/streak")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions grade(long cardId, int quality) throws Exception {
        return mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": " + quality + "}"))
                .andExpect(status().isOk());
    }

    private void setSchedule(long cardId, LocalDate dueDate, String ease) {
        jdbcTemplate.update("UPDATE cards SET due_date = ?, ease_factor = ? WHERE id = ?",
                dueDate, new java.math.BigDecimal(ease), cardId);
    }

    private void backdateReview(long cardId, int daysAgo) {
        jdbcTemplate.update("""
                INSERT INTO review_history (card_id, user_id, quality_score, previous_interval, new_interval,
                                            previous_ease_factor, new_ease_factor, reviewed_at)
                SELECT ?, d.user_id, 4, 0, 1, 2.50, 2.50, now() - make_interval(days => ?)
                FROM cards c JOIN decks d ON d.id = c.deck_id WHERE c.id = ?
                """, cardId, daysAgo, cardId);
    }

    private long createDeck(String userToken, String name) throws Exception {
        return json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long createCard(String userToken, long deck, String question) throws Exception {
        return json(mockMvc.perform(post("/api/decks/" + deck + "/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"answer\":\"A\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
}
