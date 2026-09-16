package com.recallai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.recallai.AbstractIntegrationTest;
import com.recallai.ai.AiUnavailableException;
import com.recallai.ai.FakeClaudeClient;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Phase 3 of the adaptive upgrade: mistake capture, review into a flashcard, SM-2 pickup. */
@TestPropertySource(properties = "recallai.ai.api-key=test-key")
class MistakeControllerTest extends AbstractIntegrationTest {

    private static final String QUIZ = """
            {"questions": [
              {"question": "What does DNA polymerase do?",
               "options": ["Copies DNA", "Builds proteins", "Splits water", "Stores fat"],
               "correctAnswer": 0, "explanation": "It replicates DNA before cell division.", "topic": "Genetics"},
              {"question": "Where does respiration happen?",
               "options": ["Nucleus", "Mitochondrion", "Ribosome", "Vacuole"],
               "correctAnswer": 1, "explanation": "Mitochondria respire.", "topic": "Cells"}
            ]}
            """;
    private static final String CARD = """
            {"card": {"question": "Which enzyme copies DNA before a cell divides?",
                      "answer": "DNA polymerase.",
                      "explanation": "DNA polymerase synthesises a new strand from each template. Ribosomes build proteins, which is a different job.",
                      "topic": "Genetics", "tags": ["dna", "Replication"]}}
            """;

    @Autowired
    private FakeClaudeClient fakeClaudeClient;

    private String token;
    private long deckId;
    private long quizId;
    private long q1;
    private long q2;

    @BeforeEach
    void setUp() throws Exception {
        fakeClaudeClient.reset();
        token = registerUser("owner@example.com");
        deckId = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Biology\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        fakeClaudeClient.reply(QUIZ);
        quizId = json(mockMvc.perform(post("/api/ai/quiz").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": " + deckId + ", \"text\": \"DNA polymerase copies DNA. Mitochondria respire.\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        JsonNode quiz = json(mockMvc.perform(get("/api/quizzes/" + quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn());
        q1 = quiz.get("questions").get(0).get("id").asLong();
        q2 = quiz.get("questions").get(1).get("id").asLong();
    }

    @Test
    void wrongAndSkippedAnswersBecomeMistakesAndRepeatsBumpOccurrences() throws Exception {
        submit("[{\"questionId\": " + q1 + ", \"selectedAnswer\": 1}]")
                .andExpect(jsonPath("$.results[0].mistakeId").isNumber())
                .andExpect(jsonPath("$.results[0].mistakeStatus").value("OPEN"))
                .andExpect(jsonPath("$.results[0].mistakeCardId").doesNotExist())
                .andExpect(jsonPath("$.results[1].mistakeId").isNumber());

        mockMvc.perform(get("/api/mistakes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].question").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "What does DNA polymerase do?", "Where does respiration happen?")));
        mockMvc.perform(get("/api/mistakes?topic=genetics").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].source").value("QUIZ"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].givenAnswer").value("Builds proteins"))
                .andExpect(jsonPath("$.content[0].correctAnswer").value("Copies DNA"))
                .andExpect(jsonPath("$.content[0].explanation").value("It replicates DNA before cell division."))
                .andExpect(jsonPath("$.content[0].topic").value("Genetics"))
                .andExpect(jsonPath("$.content[0].deckId").value(deckId))
                .andExpect(jsonPath("$.content[0].deckName").value("Biology"))
                .andExpect(jsonPath("$.content[0].occurrences").value(1));

        // Missing the same question again bumps the count and records the new answer; the skipped one stays.
        submit("[{\"questionId\": " + q1 + ", \"selectedAnswer\": 2}, {\"questionId\": " + q2 + ", \"selectedAnswer\": 1}]");
        mockMvc.perform(get("/api/mistakes?topic=Genetics").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].occurrences").value(2))
                .andExpect(jsonPath("$.content[0].givenAnswer").value("Splits water"));
        mockMvc.perform(get("/api/mistakes/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.openByTopic[0].topic").value(org.hamcrest.Matchers.in(new String[] {"Genetics", "Cells"})))
                .andExpect(jsonPath("$.openByTopic.length()").value(2));
        mockMvc.perform(get("/api/mistakes/recent").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].question").value("What does DNA polymerase do?"));
    }

    @Test
    void reviewingAMistakeMakesAnAiCardDueTodayAndMarksItConverted() throws Exception {
        long mistakeId = submitAndGetMistake(q1, 1);
        fakeClaudeClient.reply(CARD);

        JsonNode response = json(mockMvc.perform(post("/api/mistakes/" + mistakeId + "/flashcard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiGenerated").value(true))
                .andExpect(jsonPath("$.mistake.status").value("CONVERTED"))
                .andExpect(jsonPath("$.card.deckId").value(deckId))
                .andExpect(jsonPath("$.card.question").value("Which enzyme copies DNA before a cell divides?"))
                .andExpect(jsonPath("$.card.answer").value("DNA polymerase."))
                .andExpect(jsonPath("$.card.topic").value("Genetics"))
                .andExpect(jsonPath("$.card.origin").value("MISTAKE"))
                .andExpect(jsonPath("$.card.mistakeId").value(mistakeId))
                .andExpect(jsonPath("$.card.tags").value(org.hamcrest.Matchers.contains("dna", "replication", "mistake")))
                .andExpect(jsonPath("$.card.dueDate").value(LocalDate.now().toString()))
                .andReturn());
        long cardId = response.get("card").get("id").asLong();
        assertThat(response.get("mistake").get("cardId").asLong()).isEqualTo(cardId);

        String prompt = fakeClaudeClient.prompts().get(1).messages().get(0).content();
        assertThat(prompt).contains("Question: What does DNA polymerase do?", "Student's answer: Builds proteins",
                "Correct answer: Copies DNA", "Explanation: It replicates DNA", "Topic: Genetics");

        // SM-2 picks the card up: it is in today's queue and can be graded.
        mockMvc.perform(get("/api/reviews/due").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.cards[?(@.id == " + cardId + ")]").exists());
        mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": 4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.newInterval").value(1));

        // The attempt result now shows the link, and converting twice is refused.
        mockMvc.perform(get("/api/quizzes/" + quizId + "/attempts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$[0].id").isNumber());
        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/flashcard").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/mistakes?status=CONVERTED").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/mistakes?status=OPEN").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void whenTheModelFailsTheCardIsBuiltFromTheStoredMistake() throws Exception {
        long mistakeId = submitAndGetMistake(q1, 1);
        fakeClaudeClient.fail(new AiUnavailableException("down", false));

        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/flashcard").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.card.question").value("What does DNA polymerase do?"))
                .andExpect(jsonPath("$.card.answer").value("Copies DNA"))
                .andExpect(jsonPath("$.card.explanation").value(
                        "It replicates DNA before cell division. You answered \"Builds proteins\" last time."))
                .andExpect(jsonPath("$.card.topic").value("Genetics"))
                .andExpect(jsonPath("$.card.tags[0]").value("mistake"))
                .andExpect(jsonPath("$.card.origin").value("MISTAKE"));
    }

    @Test
    void invalidModelOutputAlsoFallsBackAfterTheCorrectiveRetry() throws Exception {
        long mistakeId = submitAndGetMistake(q2, null);
        fakeClaudeClient.reply("not json").reply("{\"card\": {\"answer\": \"x\"}}");

        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/flashcard").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.card.explanation").value("Mitochondria respire. You skipped this question last time."));
        assertThat(fakeClaudeClient.calls()).isEqualTo(3);
    }

    @Test
    void cardGoesToTheChosenDeckAndAMissingDeckIsReported() throws Exception {
        long mistakeId = submitAndGetMistake(q1, 1);
        long other = json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Revision\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        fakeClaudeClient.reply(CARD);
        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/flashcard").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deckId\": " + other + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.card.deckId").value(other));

        // Deleting the source deck keeps the mistake (deck link cleared); converting then needs a deck.
        long mistake2 = submitAndGetMistake(q2, 0);
        mockMvc.perform(delete("/api/decks/" + deckId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mistakes/" + mistake2).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deckId").doesNotExist())
                .andExpect(jsonPath("$.question").value("Where does respiration happen?"));
        mockMvc.perform(post("/api/mistakes/" + mistake2 + "/flashcard").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("choose a deck")));
    }

    @Test
    void dismissReopenDeleteAndOwnership() throws Exception {
        long mistakeId = submitAndGetMistake(q1, 1);
        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/dismiss").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        mockMvc.perform(get("/api/mistakes/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.dismissed").value(1));

        // Missing it again reopens it.
        submit("[{\"questionId\": " + q1 + ", \"selectedAnswer\": 3}, {\"questionId\": " + q2 + ", \"selectedAnswer\": 1}]");
        mockMvc.perform(get("/api/mistakes/" + mistakeId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.occurrences").value(2));

        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/dismiss").header(HttpHeaders.AUTHORIZATION, bearer(token)));
        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/reopen").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("OPEN"));

        String stranger = registerUser("stranger@example.com");
        mockMvc.perform(get("/api/mistakes/" + mistakeId).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mistakes/" + mistakeId + "/flashcard").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/mistakes").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/mistakes")).andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/mistakes/" + mistakeId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mistakes/" + mistakeId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions submit(String answers) throws Exception {
        return mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": " + answers + "}"))
                .andExpect(status().isCreated());
    }

    /** Answers one question wrongly (or skips it when {@code selected} is null) and returns its mistake id. */
    private long submitAndGetMistake(long questionId, Integer selected) throws Exception {
        String answers = selected == null
                ? "[]"
                : "[{\"questionId\": " + questionId + ", \"selectedAnswer\": " + selected + "}]";
        JsonNode result = json(submit(answers).andReturn());
        for (JsonNode r : result.get("results")) {
            if (r.get("questionId").asLong() == questionId) {
                return r.get("mistakeId").asLong();
            }
        }
        throw new AssertionError("no mistake for question " + questionId);
    }
}
