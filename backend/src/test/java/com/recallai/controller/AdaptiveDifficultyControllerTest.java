package com.recallai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Phase 2 of the adaptive upgrade: difficulty tiers riding on top of SM-2 through the API. */
class AdaptiveDifficultyControllerTest extends AbstractIntegrationTest {

    private String token;
    private long deckId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser("owner@example.com");
        deckId = createDeck("Biology");
    }

    @Test
    void newCardsStartMediumAndConfidentStreaksPromoteWithoutChangingSm2() throws Exception {
        long cardId = createCard("q");
        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.successStreak").value(0))
                .andExpect(jsonPath("$.totalReviews").value(0));

        grade(cardId, 4, 3000).andExpect(jsonPath("$.newInterval").value(1))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.difficultyChanged").value(false))
                .andExpect(jsonPath("$.successStreak").value(1));
        grade(cardId, 4, 2500).andExpect(jsonPath("$.newInterval").value(6))
                .andExpect(jsonPath("$.successStreak").value(2));
        grade(cardId, 4, 2800).andExpect(jsonPath("$.newInterval").value(15))
                .andExpect(jsonPath("$.sm2Interval").value(15))
                .andExpect(jsonPath("$.previousDifficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.difficultyChanged").value(true))
                .andExpect(jsonPath("$.successStreak").value(0));
        // Easy cards follow plain SM-2: the fourth interval is still 38.
        grade(cardId, 4, 2600).andExpect(jsonPath("$.newInterval").value(38)).andExpect(jsonPath("$.mastered").value(true));

        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.totalReviews").value(4))
                .andExpect(jsonPath("$.lapseCount").value(0));
        mockMvc.perform(get("/api/reviews/history").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].responseMs").value(2600))
                .andExpect(jsonPath("$.content[0].difficultyAfter").value("EASY"));
    }

    @Test
    void lapsesDemoteAndHardCardsComeBackSooner() throws Exception {
        long cardId = createCard("q");
        grade(cardId, 0, null).andExpect(jsonPath("$.difficulty").value("EXPERT"))
                .andExpect(jsonPath("$.newInterval").value(1));
        // Relearning: SM-2 seeds 1 and 6 are untouched even at EXPERT.
        grade(cardId, 4, null).andExpect(jsonPath("$.newInterval").value(1)).andExpect(jsonPath("$.difficulty").value("EXPERT"));
        grade(cardId, 4, null).andExpect(jsonPath("$.newInterval").value(6));
        // Third repetition: SM-2 says round(6 * ease) = 15; the third confident success promotes the
        // card to HARD and that tier's multiplier shortens the interval to 85 %.
        grade(cardId, 4, null)
                .andExpect(jsonPath("$.sm2Interval").value(15))
                .andExpect(jsonPath("$.newInterval").value(13))
                .andExpect(jsonPath("$.previousDifficulty").value("EXPERT"))
                .andExpect(jsonPath("$.difficulty").value("HARD"))
                .andExpect(jsonPath("$.difficultyChanged").value(true));

        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.interval").value(13))
                .andExpect(jsonPath("$.repetitions").value(3))
                .andExpect(jsonPath("$.lapseCount").value(1))
                .andExpect(jsonPath("$.totalReviews").value(4));
    }

    @Test
    void slowRecallsDoNotPromote() throws Exception {
        long cardId = createCard("q");
        grade(cardId, 5, 2000);
        grade(cardId, 5, 2000);
        grade(cardId, 5, 9000).andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.successStreak").value(3));
        grade(cardId, 5, 2000).andExpect(jsonPath("$.difficulty").value("EASY"));
    }

    @Test
    void distributionCountsEveryTierAndResponseTimeIsValidated() throws Exception {
        long easy = createCard("easy");
        long hard = createCard("hard");
        createCard("untouched");
        grade(easy, 5, 1000);
        grade(easy, 5, 1000);
        grade(easy, 5, 1000);
        grade(hard, 1, 3000);

        mockMvc.perform(get("/api/analytics/difficulty").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(3))
                .andExpect(jsonPath("$.tiers.length()").value(4))
                .andExpect(jsonPath("$.tiers[0].tier").value("EASY"))
                .andExpect(jsonPath("$.tiers[0].cards").value(1))
                .andExpect(jsonPath("$.tiers[0].percent").value(33))
                .andExpect(jsonPath("$.tiers[1].tier").value("MEDIUM"))
                .andExpect(jsonPath("$.tiers[1].cards").value(1))
                .andExpect(jsonPath("$.tiers[2].tier").value("HARD"))
                .andExpect(jsonPath("$.tiers[2].cards").value(1))
                .andExpect(jsonPath("$.tiers[3].cards").value(0))
                .andExpect(jsonPath("$.averageResponseMs").value(2000))
                .andExpect(jsonPath("$.cardsWithLapses").value(1));

        mockMvc.perform(post("/api/reviews/" + easy).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": 4, \"responseMs\": -5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analytics/difficulty")).andExpect(status().isUnauthorized());
    }

    private ResultActions grade(long cardId, int quality, Integer responseMs) throws Exception {
        String body = responseMs == null
                ? "{\"quality\": " + quality + "}"
                : "{\"quality\": " + quality + ", \"responseMs\": " + responseMs + "}";
        return mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private long createDeck(String name) throws Exception {
        return json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long createCard(String question) throws Exception {
        return json(mockMvc.perform(post("/api/decks/" + deckId + "/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"answer\":\"A\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
}
