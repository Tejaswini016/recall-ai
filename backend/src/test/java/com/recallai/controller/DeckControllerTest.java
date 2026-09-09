package com.recallai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DeckControllerTest extends AbstractIntegrationTest {

    private String token;

    @BeforeEach
    void registerOwner() throws Exception {
        token = registerUser("owner@example.com");
    }

    @Test
    void createReturnsNormalizedDeckWithZeroStats() throws Exception {
        mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Cell Biology ", "description": "", "subject": "Biology",
                                 "tags": ["Cells", " cells", "DNA"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Cell Biology"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.subject").value("Biology"))
                .andExpect(jsonPath("$.tags[0]").value("cells"))
                .andExpect(jsonPath("$.tags[1]").value("dna"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.cardCount").value(0))
                .andExpect(jsonPath("$.dueCount").value(0))
                .andExpect(jsonPath("$.masteredCount").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createValidatesInput() throws Exception {
        mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "tags": ["", "ok"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors['tags[0]']").isNotEmpty());
    }

    @Test
    void decksRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/decks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/decks").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUpdateAndDeleteRoundTrip() throws Exception {
        long id = createDeck(token, "Physics", "Science", "mechanics");

        mockMvc.perform(get("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Physics"));

        mockMvc.perform(put("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Physics II", "subject": "Science", "tags": ["waves"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Physics II"))
                .andExpect(jsonPath("$.tags[0]").value("waves"));

        mockMvc.perform(delete("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void usersCannotSeeUpdateOrDeleteEachOthersDecks() throws Exception {
        long id = createDeck(token, "Private", null, "secret");
        String intruder = registerUser("intruder@example.com");

        mockMvc.perform(get("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Owner still has the deck, unchanged.
        mockMvc.perform(get("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Private"));
    }

    @Test
    void listIsPaginatedAndScopedToUser() throws Exception {
        for (int i = 1; i <= 3; i++) {
            createDeck(token, "Deck " + i, "Math", "algebra");
        }
        String other = registerUser("other@example.com");
        createDeck(other, "Other deck", "Math", "algebra");

        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void searchMatchesWordsPrefixesSubjectsAndTags() throws Exception {
        createDeck(token, "Photosynthesis basics", "Biology", "plants");
        createDeck(token, "Organic chemistry", "Chemistry", "carbon");
        createDeck(token, "World War II", "History", "europe");

        // Full-text: a whole word from the name.
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("q", "chemistry"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Organic chemistry"));

        // Prefix on the name.
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("q", "photo"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Tag prefix, case-insensitive.
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("q", "EUR"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("World War II"));

        // Exact filters.
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("subject", "biology"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("tag", " Carbon "))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Organic chemistry"));

        // No match.
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)).param("q", "quantum"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deckStatsReflectCards() throws Exception {
        long id = createDeck(token, "Stats", null, "x");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/decks/" + id + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"Q" + i + "\",\"answer\":\"A\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/decks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.cardCount").value(2))
                .andExpect(jsonPath("$.dueCount").value(2))
                .andExpect(jsonPath("$.masteredCount").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0));

        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].cardCount").value(2));
    }

    private long createDeck(String userToken, String name, String subject, String tag) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("name", name);
            put("subject", subject);
            put("tags", java.util.List.of(tag));
        }});
        return json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }
}
