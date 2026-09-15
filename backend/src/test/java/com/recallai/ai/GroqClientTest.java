package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Full request/response round trips against a mocked HTTP server; no network. */
class GroqClientTest {

    private static final String BASE = "https://groq.test/openai/v1";
    private static final String CARDS = """
            {"cards": [{"question": "Q", "answer": "A", "explanation": "", "topic": "T", "tags": []}]}
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PromptService prompts = new PromptService(new FlashcardPromptBuilder(mapper), new QuizPromptBuilder(mapper));

    private static AiProperties groq(String key) {
        return new AiProperties("", "claude-opus-5", "medium", 4096, 12000, 30, 15, 1, 1, 20, false,
                AiProvider.GROQ, "", "gemini-3.6-flash", key, "llama-3.3-70b-versatile", BASE);
    }

    private record Harness(GroqClient client, MockRestServiceServer server) {
    }

    private Harness harness(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Harness(new GroqClient(groq(key), mapper, builder), server);
    }

    @Test
    void sendsAnOpenAiStyleJsonModeRequestAndParsesTheReply() {
        Harness h = harness("gsk_test");
        h.server().expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gsk_test"))
                .andExpect(jsonPath("$.model").value("llama-3.3-70b-versatile"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("<study_material>")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":%s},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":321,"completion_tokens":88}}
                        """.formatted(mapper.valueToTree(CARDS).toString()), MediaType.APPLICATION_JSON));

        AiCompletion completion = h.client().complete(prompts.flashcards("Cells have mitochondria.", 5));

        assertThat(completion.text()).contains("\"cards\"");
        assertThat(completion.truncated()).isFalse();
        assertThat(completion.inputTokens()).isEqualTo(321);
        assertThat(completion.outputTokens()).isEqualTo(88);
        h.server().verify();
    }

    @Test
    void correctiveTurnsBecomeAssistantMessagesAndLengthMeansTruncated() {
        Harness h = harness("gsk_test");
        AiPrompt corrective = prompts.corrective(prompts.flashcards("Notes", 5), "bad reply", List.of("problem"));
        ObjectNode body = h.client().toRequestBody(corrective, "m", 100);
        assertThat(body.get("messages")).hasSize(4);
        assertThat(body.get("messages").get(2).get("role").asText()).isEqualTo("assistant");
        assertThat(body.get("messages").get(2).get("content").asText()).isEqualTo("bad reply");

        h.server().expect(requestTo(BASE + "/chat/completions")).andRespond(withSuccess("""
                {"choices":[{"message":{"content":"{\\"cards\\": ["},"finish_reason":"length"}],"usage":{}}
                """, MediaType.APPLICATION_JSON));
        AiCompletion completion = h.client().complete(prompts.flashcards("Notes", 5));
        assertThat(completion.truncated()).isTrue();
    }

    @Test
    void httpErrorsMapToAccurateRetryability() {
        Harness h = harness("gsk_test");
        h.server().expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> h.client().complete(prompts.flashcards("Notes", 5)))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(e -> assertThat(((AiUnavailableException) e).isRetryable()).isTrue());

        h.server().reset();
        h.server().expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> h.client().complete(prompts.flashcards("Notes", 5)))
                .hasMessageContaining("misconfigured")
                .satisfies(e -> assertThat(((AiUnavailableException) e).isRetryable()).isFalse());

        h.server().reset();
        h.server().expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.NOT_FOUND).body("model not found"));
        assertThatThrownBy(() -> h.client().complete(prompts.flashcards("Notes", 5))).hasMessageContaining("GROQ_MODEL");

        h.server().reset();
        h.server().expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> h.client().complete(prompts.flashcards("Notes", 5)))
                .satisfies(e -> assertThat(((AiUnavailableException) e).isRetryable()).isTrue());
    }

    @Test
    void withoutAKeyNothingIsSent() {
        Harness h = harness("");
        assertThatThrownBy(() -> h.client().complete(prompts.flashcards("Notes", 5)))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("not configured");
        h.server().verify();
    }

    @Test
    void contentFilterFinishReasonIsARefusal() {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("choices").addObject().put("finish_reason", "content_filter").putObject("message").put("content", "");

        assertThatThrownBy(() -> GroqClient.parseResponse(response))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("declined");
    }
}
