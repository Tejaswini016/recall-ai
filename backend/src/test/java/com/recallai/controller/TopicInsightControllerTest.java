package com.recallai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.recallai.AbstractIntegrationTest;
import com.recallai.ai.FakeClaudeClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Phase 1 of the adaptive upgrade: combined weak-topic detection and the practice entry points. */
class TopicInsightControllerTest extends AbstractIntegrationTest {

    private static final String GENETICS_QUIZ = """
            {"questions": [
              {"question": "What does DNA polymerase do?",
               "options": ["Copies DNA", "Builds proteins", "Splits water", "Stores fat"],
               "correctAnswer": 0, "explanation": "It replicates DNA.", "topic": "Genetics"},
              {"question": "What is a codon?",
               "options": ["A lipid", "Three bases coding one amino acid", "A cell wall", "An enzyme"],
               "correctAnswer": 1, "explanation": "A codon is a base triplet.", "topic": "genetics "},
              {"question": "Where does respiration happen?",
               "options": ["Nucleus", "Mitochondrion", "Ribosome", "Vacuole"],
               "correctAnswer": 1, "explanation": "Mitochondria respire.", "topic": "Cells"}
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
        deckId = createDeck("Biology");
    }

    @Test
    void insightsCombineCardReviewsAndQuizAnswersPerTopic() throws Exception {
        long genetics1 = createCard(deckId, "g1", "Genetics");
        long genetics2 = createCard(deckId, "g2", " genetics ");
        long cells = createCard(deckId, "c1", "Cells");
        createCard(deckId, "untagged", null);
        grade(genetics1, 1);
        grade(genetics2, 4);
        grade(cells, 5);
        grade(cells, 4);
        grade(cells, 5);

        long quizId = generateQuiz(GENETICS_QUIZ);
        JsonNode quiz = json(mockMvc.perform(get("/api/quizzes/" + quizId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn());
        assertThat(quiz.get("questions").get(0).get("topic").asText()).isEqualTo("Genetics");
        long q1 = quiz.get("questions").get(0).get("id").asLong();
        long q2 = quiz.get("questions").get(1).get("id").asLong();
        long q3 = quiz.get("questions").get(2).get("id").asLong();
        // Genetics: one right, one wrong. Cells: right.
        mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": " + q1 + ", \"selectedAnswer\": 0},"
                                + " {\"questionId\": " + q2 + ", \"selectedAnswer\": 3},"
                                + " {\"questionId\": " + q3 + ", \"selectedAnswer\": 1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[1].topic").value("genetics"));

        // Genetics: 2 reviews (1 success) + 2 quiz answers (1 correct) = 4 attempts, 50 % -> WEAK.
        // Cells: 3 reviews (3 successes) + 1 quiz answer (correct) = 4 attempts, 100 % -> STRONG.
        mockMvc.perform(get("/api/analytics/topic-insights").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].topic").value("Genetics"))
                .andExpect(jsonPath("$[0].category").value("WEAK"))
                .andExpect(jsonPath("$[0].accuracyPercent").value(50))
                .andExpect(jsonPath("$[0].attempts").value(4))
                .andExpect(jsonPath("$[0].mistakes").value(2))
                .andExpect(jsonPath("$[0].cardCount").value(2))
                .andExpect(jsonPath("$[0].cardReviews").value(2))
                .andExpect(jsonPath("$[0].cardSuccesses").value(1))
                .andExpect(jsonPath("$[0].quizAnswers").value(2))
                .andExpect(jsonPath("$[0].quizCorrect").value(1))
                .andExpect(jsonPath("$[0].lastStudiedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].recommendedAction").value(org.hamcrest.Matchers.containsString("quiz")))
                .andExpect(jsonPath("$[1].topic").value("Cells"))
                .andExpect(jsonPath("$[1].category").value("STRONG"))
                .andExpect(jsonPath("$[1].accuracyPercent").value(100))
                .andExpect(jsonPath("$[1].attempts").value(4));

        mockMvc.perform(get("/api/analytics/topic-insights?weakOnly=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].topic").value("Genetics"));
    }

    @Test
    void cardsWithoutATopicCountUnderTheirDeckAndFewAttemptsAreUnrated() throws Exception {
        long untagged = createCard(deckId, "no topic", null);
        grade(untagged, 0);

        mockMvc.perform(get("/api/analytics/topic-insights").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].topic").value("Biology"))
                .andExpect(jsonPath("$[0].category").value("UNRATED"))
                .andExpect(jsonPath("$[0].accuracyPercent").value(0))
                .andExpect(jsonPath("$[0].attempts").value(1));
    }

    @Test
    void insightsAreScopedToTheOwnerAndOptionallyToADeck() throws Exception {
        long other = createDeck("Chemistry");
        long bio = createCard(deckId, "b", "Cells");
        long chem = createCard(other, "c", "Bonds");
        grade(bio, 1);
        grade(chem, 5);

        mockMvc.perform(get("/api/analytics/topic-insights?deckId=" + other)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].topic").value("Bonds"));

        String stranger = registerUser("stranger@example.com");
        mockMvc.perform(get("/api/analytics/topic-insights").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/analytics/topic-insights?deckId=" + deckId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/analytics/topic-insights")).andExpect(status().isUnauthorized());
    }

    @Test
    void practiceQueueReturnsEveryCardOnTheTopicHardestFirstRegardlessOfDueDate() throws Exception {
        long easy = createCard(deckId, "easy", "Genetics");
        long hard = createCard(deckId, "hard", "genetics");
        createCard(deckId, "other", "Cells");
        jdbcTemplate.update("UPDATE cards SET due_date = due_date + 30 WHERE id = ?", easy);
        jdbcTemplate.update("UPDATE cards SET ease_factor = 1.30 WHERE id = ?", hard);

        mockMvc.perform(get("/api/reviews/practice?topic=Genetics").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDue").value(2))
                .andExpect(jsonPath("$.cards.length()").value(2))
                .andExpect(jsonPath("$.cards[0].id").value(hard))
                .andExpect(jsonPath("$.cards[1].id").value(easy));

        mockMvc.perform(get("/api/reviews/practice?topic=Genetics&limit=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalDue").value(2))
                .andExpect(jsonPath("$.cards.length()").value(1));
        mockMvc.perform(get("/api/reviews/practice?topic=Nothing").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.cards.length()").value(0));
        mockMvc.perform(get("/api/reviews/practice").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topicQuizIsBuiltFromTheTopicsCardsAndStoredUnderTheirDeck() throws Exception {
        long other = createDeck("Chemistry");
        createCard(deckId, "What is a codon?", "Genetics");
        createCard(deckId, "What does DNA polymerase do?", "Genetics");
        createCard(other, "Which base pairs with adenine?", "genetics");
        createCard(deckId, "Where does respiration happen?", "Cells");
        fakeClaudeClient.reply(GENETICS_QUIZ);

        mockMvc.perform(post("/api/ai/quiz/topic").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \" Genetics \", \"count\": 3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deckId").value(deckId))
                .andExpect(jsonPath("$.title").value("Practice: Genetics"))
                .andExpect(jsonPath("$.questionCount").value(3));

        String prompt = fakeClaudeClient.prompts().get(0).messages().get(0).content();
        assertThat(prompt).contains("Topic: Genetics", "Q: What is a codon?", "Which base pairs with adenine?",
                "up to 3 multiple-choice questions");
        assertThat(prompt).doesNotContain("respiration");
    }

    @Test
    void topicQuizWithoutCardsIsRejectedBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post("/api/ai/quiz/topic").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"Genetics\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No cards carry")));
        mockMvc.perform(post("/api/ai/quiz/topic").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"  \"}"))
                .andExpect(status().isBadRequest());
        assertThat(fakeClaudeClient.calls()).isZero();
    }

    private long generateQuiz(String reply) throws Exception {
        fakeClaudeClient.reply(reply);
        return json(mockMvc.perform(post("/api/ai/quiz").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": " + deckId + ", \"text\": \"DNA polymerase copies DNA. A codon is three bases.\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private void grade(long cardId, int quality) throws Exception {
        mockMvc.perform(post("/api/reviews/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quality\": " + quality + "}"))
                .andExpect(status().isOk());
    }

    private long createDeck(String name) throws Exception {
        return json(mockMvc.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long createCard(long deck, String question, String topic) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        body.put("answer", "A");
        body.put("topic", topic);
        return json(mockMvc.perform(post("/api/decks/" + deck + "/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
}
