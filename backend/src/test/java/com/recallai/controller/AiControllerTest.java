package com.recallai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import com.recallai.ai.AiUnavailableException;
import com.recallai.ai.FakeClaudeClient;
import com.recallai.TestPdf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class AiControllerTest extends AbstractIntegrationTest {

    private static final String MATERIAL = "The mitochondrion produces most of a cell's ATP through respiration. "
            + "Ribosomes assemble proteins from amino acids.";
    private static final String TWO_CARDS = """
            {"cards": [
              {"question": "Which organelle produces most ATP?", "answer": "The mitochondrion.",
               "explanation": "It carries out cellular respiration.", "topic": "Cell energy", "tags": ["Organelles"]},
              {"question": "What do ribosomes assemble?", "answer": "Proteins, from amino acids.",
               "explanation": "", "topic": "Protein synthesis", "tags": ["organelles", "proteins"]}
            ]}
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
    void pastedNotesBecomePersistedCardsInTheDeck() throws Exception {
        fakeClaudeClient.reply(TWO_CARDS);

        mockMvc.perform(post("/api/ai/flashcards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("deckId", deckId);
                            put("text", MATERIAL);
                            put("count", 10);
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deckId").value(deckId))
                .andExpect(jsonPath("$.cardsCreated").value(2))
                .andExpect(jsonPath("$.chunks").value(1))
                .andExpect(jsonPath("$.cachedChunks").value(0))
                .andExpect(jsonPath("$.retries").value(0))
                .andExpect(jsonPath("$.cards[0].question").value("Which organelle produces most ATP?"))
                .andExpect(jsonPath("$.cards[0].topic").value("Cell energy"))
                .andExpect(jsonPath("$.cards[0].tags[0]").value("organelles"))
                .andExpect(jsonPath("$.cards[0].easeFactor").value(2.5))
                .andExpect(jsonPath("$.cards[1].explanation").doesNotExist());

        assertThat(fakeClaudeClient.prompts().get(0).messages().get(0).content()).contains("up to 10 flashcards");

        mockMvc.perform(get("/api/decks/" + deckId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.cardCount").value(2))
                .andExpect(jsonPath("$.dueCount").value(2));
        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalDue").value(2));
    }

    @Test
    void secondGenerationFromTheSameNotesIsServedFromCache() throws Exception {
        fakeClaudeClient.reply(TWO_CARDS);
        generateText(deckId, MATERIAL, 10).andExpect(status().isCreated());
        long otherDeck = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bio copy\"}"))
                .andReturn()).get("id").asLong();

        generateText(otherDeck, "  " + MATERIAL.toUpperCase() + " ", 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardsCreated").value(2))
                .andExpect(jsonPath("$.cachedChunks").value(1));

        assertThat(fakeClaudeClient.calls()).isEqualTo(1);
    }

    @Test
    void longMaterialIsChunkedAndDuplicatesAcrossChunksAreMerged() throws Exception {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longText.append("Fact number ").append(i).append(" is about biology topic ").append(i % 7).append(".\n\n");
        }
        assertThat(longText.length()).isGreaterThan(12000).isLessThan(36000);
        // Both chunks return the same first card; only one copy may be saved.
        fakeClaudeClient.reply(TWO_CARDS).reply(TWO_CARDS.replace("What do ribosomes assemble?", "What is DNA?"));

        generateText(deckId, longText.toString(), 20)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunks").value(fakeClaudeClient.calls()))
                .andExpect(jsonPath("$.cardsCreated").value(3));

        assertThat(fakeClaudeClient.calls()).isBetween(2, 3);
        assertThat(fakeClaudeClient.prompts().get(0).messages().get(0).content()).doesNotContain("up to 20 flashcards");
    }

    @Test
    void requestedCountCapsTheMergedResult() throws Exception {
        fakeClaudeClient.reply(TWO_CARDS);

        generateText(deckId, MATERIAL, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardsCreated").value(1));
    }

    @Test
    void txtUploadIsExtractedAndGenerated() throws Exception {
        fakeClaudeClient.reply(TWO_CARDS);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                MATERIAL.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/ai/flashcards/upload").file(file)
                        .param("deckId", String.valueOf(deckId)).param("count", "5")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardsCreated").value(2));

        assertThat(fakeClaudeClient.prompts().get(0).messages().get(0).content()).contains("Ribosomes assemble proteins");
    }

    @Test
    void pdfUploadIsExtractedAndGenerated() throws Exception {
        fakeClaudeClient.reply(TWO_CARDS);
        MockMultipartFile file = new MockMultipartFile("file", "chapter.pdf", "application/pdf",
                TestPdf.withText("Mitochondria produce ATP. Ribosomes assemble proteins."));

        mockMvc.perform(multipart("/api/ai/flashcards/upload").file(file)
                        .param("deckId", String.valueOf(deckId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardsCreated").value(2));

        assertThat(fakeClaudeClient.prompts().get(0).messages().get(0).content())
                .contains("Mitochondria produce ATP")
                .contains("up to 15 flashcards");
    }

    @Test
    void badUploadsAreRejectedWithoutCallingTheModel() throws Exception {
        MockMultipartFile docx = new MockMultipartFile("file", "notes.docx", "application/octet-stream",
                new byte[] {1, 2, 3});
        MockMultipartFile empty = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[0]);
        MockMultipartFile fakePdf = new MockMultipartFile("file", "x.pdf", "application/pdf",
                "nope".getBytes(StandardCharsets.UTF_8));

        for (MockMultipartFile file : new MockMultipartFile[] {docx, empty, fakePdf}) {
            mockMvc.perform(multipart("/api/ai/flashcards/upload").file(file)
                            .param("deckId", String.valueOf(deckId))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("FILE_UPLOAD_ERROR"));
        }
        assertThat(fakeClaudeClient.calls()).isZero();
    }

    @Test
    void invalidRequestsAreRejectedBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post("/api/ai/flashcards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": " + deckId + ", \"text\": \"   \", \"count\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.text").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.count").isNotEmpty());

        generateText(deckId, "x".repeat(70_000), 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("too long")));

        mockMvc.perform(post("/api/ai/flashcards").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": 1, \"text\": \"notes\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(fakeClaudeClient.calls()).isZero();
    }

    @Test
    void cannotGenerateIntoAnotherUsersDeck() throws Exception {
        String intruder = registerUser("intruder@example.com");

        mockMvc.perform(post("/api/ai/flashcards").header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": " + deckId + ", \"text\": \"" + MATERIAL + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(fakeClaudeClient.calls()).isZero();
    }

    @Test
    void aiFailureReturnsControlledErrorAndSavesNothing() throws Exception {
        fakeClaudeClient.reply("not json").reply("still not json");

        generateText(deckId, MATERIAL, 5)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("invalid response")));

        fakeClaudeClient.reset();
        fakeClaudeClient.fail(new AiUnavailableException("The AI service is busy; please try again shortly", false));
        generateText(deckId, MATERIAL + " more", 5)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_ERROR"));

        mockMvc.perform(get("/api/decks/" + deckId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.cardCount").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions generateText(long deck, String text, int count)
            throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("deckId", deck);
            put("text", text);
            put("count", count);
        }});
        return mockMvc.perform(post("/api/ai/flashcards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
