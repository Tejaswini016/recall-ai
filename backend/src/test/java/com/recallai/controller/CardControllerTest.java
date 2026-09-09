package com.recallai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class CardControllerTest extends AbstractIntegrationTest {

    private String token;
    private long deckId;

    @BeforeEach
    void setUpDeck() throws Exception {
        token = registerUser("owner@example.com");
        deckId = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Biology\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void createCardStartsWithSm2DefaultsAndIsDueToday() throws Exception {
        mockMvc.perform(post("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": " What is ATP? ", "answer": "Adenosine triphosphate",
                                 "explanation": "The cell's energy currency.", "topic": "Cell energy",
                                 "tags": ["Energy", "energy", "Cells"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deckId").value(deckId))
                .andExpect(jsonPath("$.question").value("What is ATP?"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.easeFactor").value(2.5))
                .andExpect(jsonPath("$.interval").value(0))
                .andExpect(jsonPath("$.repetitions").value(0))
                .andExpect(jsonPath("$.dueDate").value(LocalDate.now().toString()));
    }

    @Test
    void createCardValidatesInput() throws Exception {
        mockMvc.perform(post("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"answer\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.question").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.answer").isNotEmpty());
    }

    @Test
    void updateChangesContentButNeverSchedulingFields() throws Exception {
        long cardId = createCard(token, deckId, "Q1", "A1", "t1", "one");

        mockMvc.perform(put("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Q1 edited", "answer": "A1 edited", "topic": "t2", "tags": ["two"],
                                 "easeFactor": 9.99, "interval": 400, "repetitions": 12, "dueDate": "2030-01-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("Q1 edited"))
                .andExpect(jsonPath("$.topic").value("t2"))
                .andExpect(jsonPath("$.tags[0]").value("two"))
                .andExpect(jsonPath("$.easeFactor").value(2.5))
                .andExpect(jsonPath("$.interval").value(0))
                .andExpect(jsonPath("$.repetitions").value(0))
                .andExpect(jsonPath("$.dueDate").value(LocalDate.now().toString()));
    }

    @Test
    void deleteRemovesCard() throws Exception {
        long cardId = createCard(token, deckId, "Q", "A", null, "x");

        mockMvc.perform(delete("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingDeckCascadesToCards() throws Exception {
        long cardId = createCard(token, deckId, "Q", "A", null, "x");

        mockMvc.perform(delete("/api/decks/" + deckId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cardsAreIsolatedBetweenUsers() throws Exception {
        long cardId = createCard(token, deckId, "Secret Q", "Secret A", null, "x");
        String intruder = registerUser("intruder@example.com");

        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\",\"answer\":\"y\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\",\"answer\":\"y\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards").header(HttpHeaders.AUTHORIZATION, bearer(intruder)).param("q", "Secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void cardSearchWithinDeckAndAcrossDecks() throws Exception {
        createCard(token, deckId, "What does mitochondria do?", "Produces ATP", "Cell energy", "organelles");
        createCard(token, deckId, "What is osmosis?", "Diffusion of water", "Transport", "membranes");
        long otherDeck = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Chemistry\"}"))
                .andReturn()).get("id").asLong();
        createCard(token, otherDeck, "What is a mole?", "Avogadro's number of particles", "Units", "organelles");

        // Full-text over question/answer within the deck (stemmed: "produces" matches "produce").
        mockMvc.perform(get("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("q", "produce"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].topic").value("Cell energy"));

        // Topic prefix and exact topic filter.
        mockMvc.perform(get("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("q", "trans"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("topic", "transport"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Tag filter across all decks.
        mockMvc.perform(get("/api/cards").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("tag", "organelles"))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Unfiltered deck listing.
        mockMvc.perform(get("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void globalSearchAndTags() throws Exception {
        createCard(token, deckId, "Explain glycolysis", "Breaks glucose into pyruvate", "Metabolism", "pathways");
        mockMvc.perform(put("/api/decks/" + deckId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Biology\",\"tags\":[\"Science\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/search").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("q", "glucose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decks.length()").value(0))
                .andExpect(jsonPath("$.cards.length()").value(1));
        mockMvc.perform(get("/api/search").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("q", "biology"))
                .andExpect(jsonPath("$.decks.length()").value(1));
        mockMvc.perform(get("/api/search").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("q", "  "))
                .andExpect(jsonPath("$.decks.length()").value(0))
                .andExpect(jsonPath("$.cards.length()").value(0));

        mockMvc.perform(get("/api/tags").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("pathways"))
                .andExpect(jsonPath("$[1]").value("science"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    private long createCard(String userToken, long deck, String question, String answer, String topic, String tag)
            throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("question", question);
            put("answer", answer);
            put("topic", topic);
            put("tags", java.util.List.of(tag));
        }});
        return json(mockMvc.perform(post("/api/decks/" + deck + "/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }
}
