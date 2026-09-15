package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.errors.ClientException;
import com.google.genai.types.Content;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers the provider mapping without any network access. */
class GeminiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PromptService prompts = new PromptService(new FlashcardPromptBuilder(mapper), new QuizPromptBuilder(mapper));

    private static AiProperties gemini(String key) {
        return new AiProperties("", "claude-opus-5", "medium", 4096, 12000, 30, 15, 1, 1, 20, false,
                AiProvider.GEMINI, key, "gemini-3.6-flash", "", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1");
    }

    @Test
    void withoutAKeyTheClientReportsNotConfiguredInsteadOfCrashing() {
        GeminiClient client = new GeminiClient(gemini(""), mapper);

        assertThatThrownBy(() -> client.complete(prompts.flashcards("Notes", 5)))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("not configured")
                .satisfies(e -> assertThat(((AiUnavailableException) e).isRetryable()).isFalse());
    }

    @Test
    void turnsMapToGeminiRolesWithAssistantBecomingModel() {
        AiPrompt corrective = prompts.corrective(prompts.flashcards("Notes", 5), "bad reply", List.of("problem"));

        List<Content> contents = GeminiClient.toContents(corrective);

        assertThat(contents).hasSize(3);
        assertThat(contents.get(0).role()).contains("user");
        assertThat(contents.get(1).role()).contains("model");
        assertThat(contents.get(2).role()).contains("user");
        assertThat(contents.get(1).parts().orElseThrow().get(0).text()).contains("bad reply");
    }

    @Test
    void schemaIsSentAsStandardJsonSchemaWithoutUnsupportedKeywords() {
        GeminiClient client = new GeminiClient(gemini(""), mapper);
        JsonNode original = prompts.flashcards("Notes", 5).outputSchema();

        Map<String, Object> sent = client.toSchemaMap(original);

        assertThat(sent).containsKey("properties").doesNotContainKey("additionalProperties");
        @SuppressWarnings("unchecked")
        Map<String, Object> cards = (Map<String, Object>) ((Map<String, Object>) sent.get("properties")).get("cards");
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) cards.get("items");
        assertThat(items).doesNotContainKey("additionalProperties").containsKey("required");
        // The original prompt schema is untouched.
        assertThat(original.get("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void clientErrorsMapToAccurateRetryability() {
        AiUnavailableException unauthorized = GeminiClient.translateClientError(new ClientException(401, "UNAUTHENTICATED", "bad key"));
        AiUnavailableException rateLimited = GeminiClient.translateClientError(new ClientException(429, "RESOURCE_EXHAUSTED", "quota"));
        AiUnavailableException badRequest = GeminiClient.translateClientError(new ClientException(400, "INVALID_ARGUMENT", "schema"));

        assertThat(unauthorized.isRetryable()).isFalse();
        assertThat(unauthorized.getMessage()).contains("misconfigured");
        assertThat(rateLimited.isRetryable()).isTrue();
        assertThat(rateLimited.getMessage()).contains("busy");
        assertThat(badRequest.isRetryable()).isFalse();

        AiUnavailableException retiredModel = GeminiClient.translateClientError(
                new ClientException(404, "NOT_FOUND", "This model is no longer available"));
        assertThat(retiredModel.isRetryable()).isFalse();
        assertThat(retiredModel.getMessage()).contains("GEMINI_MODEL");
    }

    @Test
    void propertiesChooseModelKeyAndAvailabilityPerProvider() {
        AiProperties withGemini = gemini("g-key");
        AiProperties anthropic = new AiProperties("c-key", "claude-opus-5", "medium", 4096, 12000, 30, 15, 1, 1, 20, false,
                AiProvider.ANTHROPIC, "", "gemini-3.6-flash", "", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1");
        AiProperties demo = new AiProperties("", "claude-opus-5", "medium", 4096, 12000, 30, 15, 1, 1, 20, true,
                AiProvider.GEMINI, "", "gemini-3.6-flash", "", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1");

        assertThat(withGemini.effectiveModel()).isEqualTo("gemini-3.6-flash", "", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1");
        assertThat(withGemini.effectiveProvider()).isEqualTo("gemini");
        assertThat(withGemini.generationAvailable()).isTrue();
        assertThat(gemini("").generationAvailable()).isFalse();
        assertThat(anthropic.effectiveModel()).isEqualTo("claude-opus-5");
        assertThat(anthropic.generationAvailable()).isTrue();
        assertThat(demo.effectiveModel()).isEqualTo("demo");
        assertThat(demo.effectiveProvider()).isEqualTo("demo");
        assertThat(demo.generationAvailable()).isTrue();
    }
}
