package com.recallai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.recallai.AbstractIntegrationTest;
import com.recallai.ai.FakeClaudeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class QuizControllerTest extends AbstractIntegrationTest {

    private static final String TWO_QUESTIONS = """
            {"questions": [
              {"question": "Which organelle produces most ATP?",
               "options": ["Nucleus", "Mitochondrion", "Ribosome", "Golgi apparatus"],
               "correctAnswer": 1, "explanation": "Mitochondria carry out cellular respiration."},
              {"question": "What do ribosomes build?",
               "options": ["Proteins", "Lipids", "DNA", "ATP"],
               "correctAnswer": 0, "explanation": "Ribosomes translate mRNA into proteins."}
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
    void quizIsGeneratedFromTheDecksCardsAndHidesAnswers() throws Exception {
        createCard("Which organelle produces most ATP?", "The mitochondrion.", "It runs respiration.");
        createCard("What do ribosomes build?", "Proteins.", null);
        fakeClaudeClient.reply(TWO_QUESTIONS);

        long quizId = json(generateQuiz("{\"deckId\": " + deckId + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deckId").value(deckId))
                .andExpect(jsonPath("$.deckName").value("Biology"))
                .andExpect(jsonPath("$.title").value("Biology quiz"))
                .andExpect(jsonPath("$.questionCount").value(2))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.bestScorePercent").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.questions[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.questions[0].explanation").doesNotExist())
                .andReturn()).get("id").asLong();

        String prompt = fakeClaudeClient.prompts().get(0).messages().get(0).content();
        assertThat(prompt).contains("Q: Which organelle produces most ATP?", "A: The mitochondrion.",
                "It runs respiration.", "up to 10 multiple-choice questions");

        mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[1].question").value("What do ribosomes build?"))
                .andExpect(jsonPath("$.questions[1].correctAnswer").doesNotExist());
    }

    @Test
    void quizCanBeGeneratedFromSuppliedMaterialWithCustomTitleAndCount() throws Exception {
        fakeClaudeClient.reply(TWO_QUESTIONS);

        generateQuiz("{\"deckId\": " + deckId + ", \"title\": \"  Cells 101 \", \"count\": 5, "
                + "\"text\": \"Mitochondria produce ATP. Ribosomes build proteins.\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Cells 101"))
                .andExpect(jsonPath("$.questionCount").value(2));

        String prompt = fakeClaudeClient.prompts().get(0).messages().get(0).content();
        assertThat(prompt).contains("up to 5 multiple-choice questions", "Mitochondria produce ATP.");
    }

    @Test
    void emptyDeckWithoutMaterialIsRejectedBeforeCallingTheModel() throws Exception {
        generateQuiz("{\"deckId\": " + deckId + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no cards yet")));
        assertThat(fakeClaudeClient.calls()).isZero();
    }

    @Test
    void submittingAnAttemptScoresAndExplains() throws Exception {
        long quizId = generateQuizFromText();
        JsonNode quiz = json(mockMvc.perform(get("/api/quizzes/" + quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn());
        long q1 = quiz.get("questions").get(0).get("id").asLong();
        long q2 = quiz.get("questions").get(1).get("id").asLong();

        long attemptId = json(mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": " + q1 + ", \"selectedAnswer\": 1},"
                                + " {\"questionId\": " + q2 + ", \"selectedAnswer\": 3}], \"durationSeconds\": 42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quizId").value(quizId))
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.percent").value(50))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.incorrectCount").value(1))
                .andExpect(jsonPath("$.durationSeconds").value(42))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.results[0].correct").value(true))
                .andExpect(jsonPath("$.results[0].selectedAnswer").value(1))
                .andExpect(jsonPath("$.results[0].correctAnswer").value(1))
                .andExpect(jsonPath("$.results[1].correct").value(false))
                .andExpect(jsonPath("$.results[1].selectedAnswer").value(3))
                .andExpect(jsonPath("$.results[1].correctAnswer").value(0))
                .andExpect(jsonPath("$.results[1].explanation").value("Ribosomes translate mRNA into proteins."))
                .andReturn()).get("id").asLong();

        mockMvc.perform(get("/api/quizzes/" + quizId + "/attempts/" + attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[1].options.length()").value(4));

        mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.bestScorePercent").value(50));
    }

    @Test
    void retryingImprovesBestScoreAndAttemptsAreListedNewestFirst() throws Exception {
        long quizId = generateQuizFromText();
        JsonNode quiz = json(mockMvc.perform(get("/api/quizzes/" + quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn());
        long q1 = quiz.get("questions").get(0).get("id").asLong();
        long q2 = quiz.get("questions").get(1).get("id").asLong();

        submit(quizId, "[]").andExpect(jsonPath("$.percent").value(0));
        submit(quizId, "[{\"questionId\": " + q1 + ", \"selectedAnswer\": 1}, {\"questionId\": " + q2
                + ", \"selectedAnswer\": 0}]").andExpect(jsonPath("$.percent").value(100));

        mockMvc.perform(get("/api/quizzes/" + quizId + "/attempts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].percent").value(100))
                .andExpect(jsonPath("$[1].percent").value(0));

        mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("deckId", String.valueOf(deckId)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].questionCount").value(2))
                .andExpect(jsonPath("$.content[0].attemptCount").value(2))
                .andExpect(jsonPath("$.content[0].bestScorePercent").value(100));
    }

    @Test
    void invalidSubmissionsAreRejected() throws Exception {
        long quizId = generateQuizFromText();
        JsonNode quiz = json(mockMvc.perform(get("/api/quizzes/" + quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn());
        long q1 = quiz.get("questions").get(0).get("id").asLong();

        submit(quizId, "[{\"questionId\": 999999, \"selectedAnswer\": 0}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not belong")));
        submit(quizId, "[{\"questionId\": " + q1 + ", \"selectedAnswer\": 0}, {\"questionId\": " + q1
                + ", \"selectedAnswer\": 1}]")
                .andExpect(status().isBadRequest());
        submit(quizId, "[{\"questionId\": " + q1 + ", \"selectedAnswer\": 7}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/quizzes/" + quizId + "/attempts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void quizzesAreIsolatedBetweenUsers() throws Exception {
        long quizId = generateQuizFromText();
        String intruder = registerUser("intruder@example.com");

        mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts").header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": []}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(post("/api/ai/quiz").header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": " + deckId + ", \"text\": \"stuff\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/quizzes")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAQuizRemovesItsAttempts() throws Exception {
        long quizId = generateQuizFromText();
        submit(quizId, "[]").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM quiz_attempts", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM quiz_attempt_answers", Integer.class)).isZero();
    }

    @Test
    void generationFailureStoresNothing() throws Exception {
        fakeClaudeClient.reply("nope").reply("{\"questions\": []}");

        generateQuiz("{\"deckId\": " + deckId + ", \"text\": \"Mitochondria produce ATP.\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_INVALID_RESPONSE"));
        mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private long generateQuizFromText() throws Exception {
        fakeClaudeClient.reply(TWO_QUESTIONS);
        return json(generateQuiz("{\"deckId\": " + deckId + ", \"text\": \"Mitochondria produce ATP. "
                + "Ribosomes build proteins.\"}").andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private ResultActions generateQuiz(String body) throws Exception {
        return mockMvc.perform(post("/api/ai/quiz").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions submit(long quizId, String answers) throws Exception {
        return mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": " + answers + "}"));
    }

    private void createCard(String question, String answer, String explanation) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("question", question);
            put("answer", answer);
            put("explanation", explanation);
        }});
        mockMvc.perform(post("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
