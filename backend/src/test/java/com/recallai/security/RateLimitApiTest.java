package com.recallai.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import com.recallai.ai.FakeClaudeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "recallai.ai.rate-limit-per-hour=2")
class RateLimitApiTest extends AbstractIntegrationTest {

    private static final String CARDS = """
            {"cards": [{"question": "Q", "answer": "A", "explanation": "", "topic": "T", "tags": []}]}
            """;

    @Autowired
    private FakeClaudeClient fakeClaudeClient;

    @Test
    void aiEndpointsReturn429WithRetryAfterOnceTheHourlyLimitIsSpent() throws Exception {
        fakeClaudeClient.reset();
        fakeClaudeClient.reply(CARDS).reply(CARDS);
        String token = registerUser("limited@example.com");
        long deckId = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"D\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        generate(token, deckId, "first material about cells")
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        generate(token, deckId, "second material about proteins")
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        generate(token, deckId, "third material about lipids")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("try again in")));

        // Other users and non-AI endpoints are unaffected.
        String other = registerUser("other@example.com");
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCallsAreRejectedBeforeCountingAgainstAnyLimit() throws Exception {
        mockMvc.perform(post("/api/ai/flashcards").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": 1, \"text\": \"notes\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));
    }

    private org.springframework.test.web.servlet.ResultActions generate(String token, long deckId, String text)
            throws Exception {
        return mockMvc.perform(post("/api/ai/flashcards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deckId\": " + deckId + ", \"text\": \"" + text + "\"}"));
    }
}
