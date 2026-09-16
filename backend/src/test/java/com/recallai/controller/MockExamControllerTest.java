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
import org.springframework.test.context.TestPropertySource;

/** Phase 5 of the adaptive upgrade: timed mixed-type mock exams graded locally. */
@TestPropertySource(properties = "recallai.ai.api-key=test-key")
class MockExamControllerTest extends AbstractIntegrationTest {

    private static final String EXAM = """
            {"questions": [
              {"type": "MCQ", "question": "Which enzyme copies DNA?",
               "options": ["DNA polymerase", "Ribosome", "Helicase", "Ligase"], "correctOption": 0,
               "correctAnswer": "", "acceptableAnswers": [], "explanation": "Polymerase synthesises the new strand.",
               "topic": "Genetics"},
              {"type": "TRUE_FALSE", "question": "Mitochondria carry out cellular respiration.",
               "options": ["True", "False"], "correctOption": 0, "correctAnswer": "", "acceptableAnswers": [],
               "explanation": "They do.", "topic": "Cells"},
              {"type": "SHORT_ANSWER", "question": "Name the organelle that builds proteins.",
               "options": [], "correctOption": -1, "correctAnswer": "Ribosome", "acceptableAnswers": ["ribosomes"],
               "explanation": "Ribosomes translate mRNA.", "topic": "Cells"},
              {"type": "MCQ", "question": "What is a codon?",
               "options": ["Three bases", "A lipid", "A cell wall", "An enzyme"], "correctOption": 0,
               "correctAnswer": "", "acceptableAnswers": [], "explanation": "A codon is a base triplet.",
               "topic": "Genetics"}
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
        createCard("Which enzyme copies DNA?", "DNA polymerase", "Genetics");
        createCard("What is a codon?", "Three bases", "Genetics");
        createCard("Which organelle builds proteins?", "The ribosome", "Cells");
    }

    @Test
    void examIsGeneratedWithoutAnswersAndGradedOnSubmission() throws Exception {
        fakeClaudeClient.reply(EXAM);
        JsonNode exam = json(mockMvc.perform(post("/api/mock-exams").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\": \"HARD\", \"questionCount\": 4, \"durationMinutes\": 20,"
                                + " \"questionTypes\": [\"MCQ\", \"TRUE_FALSE\", \"SHORT_ANSWER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Mock exam: all cards"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.difficulty").value("HARD"))
                .andExpect(jsonPath("$.durationMinutes").value(20))
                .andExpect(jsonPath("$.remainingSeconds").value(org.hamcrest.Matchers.greaterThan(1100)))
                .andExpect(jsonPath("$.questionCount").value(4))
                .andExpect(jsonPath("$.questions[0].type").value("MCQ"))
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.questions[0].correctOption").doesNotExist())
                .andExpect(jsonPath("$.questions[2].type").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.questions[2].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.questions[2].options.length()").value(0))
                .andReturn());
        String prompt = fakeClaudeClient.prompts().get(0).messages().get(0).content();
        assertThat(prompt).contains("Allowed question types: MCQ, SHORT_ANSWER, TRUE_FALSE", "Difficulty: hard",
                "Topic: Genetics", "Q: Which enzyme copies DNA?");

        long examId = exam.get("id").asLong();
        long q1 = exam.get("questions").get(0).get("id").asLong();
        long q2 = exam.get("questions").get(1).get("id").asLong();
        long q3 = exam.get("questions").get(2).get("id").asLong();
        // q4 skipped. MCQ right, true/false wrong, short answer right via an acceptable alternative.
        mockMvc.perform(get("/api/mock-exams/" + examId + "/results").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/mock-exams/" + examId + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": " + q1 + ", \"selectedOption\": 0},"
                                + " {\"questionId\": " + q2 + ", \"selectedOption\": 1},"
                                + " {\"questionId\": " + q3 + ", \"answerText\": \" the RIBOSOMES \"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2))
                .andExpect(jsonPath("$.totalQuestions").value(4))
                .andExpect(jsonPath("$.percent").value(50))
                .andExpect(jsonPath("$.correctCount").value(2))
                .andExpect(jsonPath("$.incorrectCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.timedOut").value(false))
                .andExpect(jsonPath("$.timeTakenSeconds").isNumber())
                .andExpect(jsonPath("$.topics.length()").value(2))
                .andExpect(jsonPath("$.topics[0].topic").value("Genetics"))
                .andExpect(jsonPath("$.topics[0].percent").value(50))
                .andExpect(jsonPath("$.topics[0].strong").value(false))
                .andExpect(jsonPath("$.topics[1].topic").value("Cells"))
                .andExpect(jsonPath("$.topics[1].percent").value(50))
                .andExpect(jsonPath("$.weakTopics").value(org.hamcrest.Matchers.containsInAnyOrder("Genetics", "Cells")))
                .andExpect(jsonPath("$.strongTopics.length()").value(0))
                .andExpect(jsonPath("$.questions[0].correct").value(true))
                .andExpect(jsonPath("$.questions[0].correctOption").value(0))
                .andExpect(jsonPath("$.questions[0].mistakeId").doesNotExist())
                .andExpect(jsonPath("$.questions[1].correct").value(false))
                .andExpect(jsonPath("$.questions[1].selectedOption").value(1))
                .andExpect(jsonPath("$.questions[1].correctAnswer").value("True"))
                .andExpect(jsonPath("$.questions[1].mistakeId").isNumber())
                .andExpect(jsonPath("$.questions[1].mistakeStatus").value("OPEN"))
                .andExpect(jsonPath("$.questions[2].correct").value(true))
                .andExpect(jsonPath("$.questions[2].answerText").value("the RIBOSOMES"))
                .andExpect(jsonPath("$.questions[2].correctAnswer").value("Ribosome"))
                .andExpect(jsonPath("$.questions[3].skipped").value(true))
                .andExpect(jsonPath("$.questions[3].mistakeId").isNumber());

        // Results are repeatable, a second submission is refused, mistakes carry the exam source.
        mockMvc.perform(get("/api/mock-exams/" + examId + "/results").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.percent").value(50));
        mockMvc.perform(post("/api/mock-exams/" + examId + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": []}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/mistakes?source=MOCK_EXAM").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].givenAnswer").value(org.hamcrest.Matchers.containsInAnyOrder("False", null)))
                .andExpect(jsonPath("$.content[*].correctAnswer").value(org.hamcrest.Matchers.containsInAnyOrder("True", "Three bases")));

        // Exam answers feed topic insight alongside reviews.
        mockMvc.perform(get("/api/mock-exams/stats").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.exams").value(1))
                .andExpect(jsonPath("$.submitted").value(1))
                .andExpect(jsonPath("$.averagePercent").value(50))
                .andExpect(jsonPath("$.bestPercent").value(50))
                .andExpect(jsonPath("$.latestPercent").value(50))
                .andExpect(jsonPath("$.recent[0].id").value(examId))
                .andExpect(jsonPath("$.recent[0].status").value("SUBMITTED"));
        mockMvc.perform(get("/api/mock-exams").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].percent").value(50));
    }

    @Test
    void lateSubmissionIsGradedButFlaggedAndTopicScopedExamsUseTopicCards() throws Exception {
        fakeClaudeClient.reply(EXAM);
        long examId = json(mockMvc.perform(post("/api/mock-exams").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"genetics\", \"difficulty\": \"EASY\", \"questionCount\": 4, \"durationMinutes\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Mock exam: genetics"))
                .andExpect(jsonPath("$.deckId").value(deckId))
                .andReturn()).get("id").asLong();
        assertThat(fakeClaudeClient.prompts().get(0).messages().get(0).content())
                .contains("What is a codon?").doesNotContain("builds proteins");

        jdbcTemplate.update("UPDATE mock_exams SET started_at = now() - interval '10 minutes' WHERE id = ?", examId);
        mockMvc.perform(get("/api/mock-exams/" + examId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.remainingSeconds").value(0));
        mockMvc.perform(post("/api/mock-exams/" + examId + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": []}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timedOut").value(true))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.skippedCount").value(4));
    }

    @Test
    void invalidRequestsAndOwnership() throws Exception {
        mockMvc.perform(post("/api/mock-exams").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\": \"EASY\", \"questionCount\": 1, \"durationMinutes\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.questionCount").exists())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());
        mockMvc.perform(post("/api/mock-exams").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"Nothing\", \"difficulty\": \"EASY\", \"questionCount\": 5, \"durationMinutes\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No cards carry")));
        assertThat(fakeClaudeClient.calls()).isZero();

        // A disallowed type in the reply is rejected, corrected once, then surfaces as an AI error.
        fakeClaudeClient.reply(EXAM).reply(EXAM);
        mockMvc.perform(post("/api/mock-exams").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\": \"EASY\", \"questionCount\": 4, \"durationMinutes\": 10, \"questionTypes\": [\"MCQ\"]}"))
                .andExpect(status().isBadGateway());
        assertThat(fakeClaudeClient.calls()).isEqualTo(2);

        fakeClaudeClient.reset();
        fakeClaudeClient.reply(EXAM);
        JsonNode exam = json(mockMvc.perform(post("/api/mock-exams").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deckId\": " + deckId + ", \"difficulty\": \"MEDIUM\", \"questionCount\": 4, \"durationMinutes\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Mock exam: Biology"))
                .andReturn());
        long examId = exam.get("id").asLong();
        mockMvc.perform(post("/api/mock-exams/" + examId + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": 999999, \"selectedOption\": 0}]}"))
                .andExpect(status().isBadRequest());

        String stranger = registerUser("stranger@example.com");
        mockMvc.perform(get("/api/mock-exams/" + examId).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mock-exams/" + examId + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": []}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/mock-exams")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/mock-exams/" + examId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mock-exams/stats").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.exams").value(0))
                .andExpect(jsonPath("$.averagePercent").doesNotExist());
    }

    private void createCard(String question, String answer, String topic) throws Exception {
        mockMvc.perform(post("/api/decks/" + deckId + "/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"answer\":\"" + answer + "\",\"topic\":\"" + topic + "\"}"))
                .andExpect(status().isCreated());
    }
}
