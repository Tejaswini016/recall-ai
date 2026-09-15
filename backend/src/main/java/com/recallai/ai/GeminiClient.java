package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Google Gemini access through the official Java SDK, selected with {@code AI_PROVIDER=gemini}.
 * Maps the provider-neutral {@link AiPrompt} (system text, user/model turns, JSON Schema) onto
 * a {@code generateContent} call with a JSON response constraint, and every failure onto
 * {@link AiUnavailableException} with an accurate retryable flag. Study content is never logged.
 *
 * <p>Gemini's Flash models have a free developer tier, which makes this the zero-cost path for
 * development; Claude remains the default provider.
 */
@Component
@ConditionalOnExpression("'${recallai.ai.demo-mode:false}' != 'true' && '${recallai.ai.provider:anthropic}'.toLowerCase() == 'gemini'")
public class GeminiClient implements ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    static final String ROLE_USER = "user";
    static final String ROLE_MODEL = "model";
    private static final String JSON_MIME = "application/json";
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_BAD_REQUEST = 400;
    /** JSON Schema keywords Gemini's schema dialect does not accept; harmless to drop because the validator re-checks. */
    private static final Set<String> UNSUPPORTED_SCHEMA_KEYS = Set.of("additionalProperties");
    private static final Set<String> BLOCKED_FINISH_REASONS = Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "RECITATION", "SPII");

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Client client;

    public GeminiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (properties.hasGeminiApiKey()) {
            this.client = Client.builder().apiKey(properties.geminiApiKey()).build();
            log.info("Gemini client configured for model {}", properties.geminiModel());
        } else {
            this.client = null;
            log.warn("GEMINI_API_KEY is not set; AI generation endpoints will return an error");
        }
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        if (client == null) {
            throw new AiUnavailableException("AI generation is not configured on this server", false);
        }
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(prompt.system())))
                .responseMimeType(JSON_MIME)
                .responseJsonSchema(toSchemaMap(prompt.outputSchema()))
                .maxOutputTokens((int) Math.min(properties.maxTokens(), Integer.MAX_VALUE))
                .build();

        long started = System.nanoTime();
        GenerateContentResponse response;
        try {
            response = client.models.generateContent(properties.geminiModel(), toContents(prompt), config);
        } catch (ClientException e) {
            throw translateClientError(e);
        } catch (ServerException e) {
            throw new AiUnavailableException("The AI service is temporarily unavailable", true, e);
        } catch (GenAiIOException e) {
            throw new AiUnavailableException("Could not reach the AI service", true, e);
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        String finishReason = response.candidates()
                .filter(list -> !list.isEmpty())
                .flatMap(list -> list.get(0).finishReason())
                .map(Object::toString)
                .orElse("UNKNOWN");
        long inputTokens = response.usageMetadata().flatMap(u -> u.promptTokenCount()).orElse(0);
        long outputTokens = response.usageMetadata().flatMap(u -> u.candidatesTokenCount()).orElse(0);
        log.info("Gemini {} call finished in {} ms: finish={} in={} out={}",
                prompt.operation(), elapsedMs, finishReason, inputTokens, outputTokens);

        if (BLOCKED_FINISH_REASONS.contains(finishReason)) {
            throw new AiUnavailableException("The AI declined to process this material", false);
        }
        String text = response.text();
        return new AiCompletion(text == null ? "" : text, "MAX_TOKENS".equals(finishReason), inputTokens, outputTokens);
    }

    static AiUnavailableException translateClientError(ClientException e) {
        int code = e.code();
        if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) {
            log.error("Gemini API rejected the configured API key (HTTP {})", code);
            return new AiUnavailableException("AI generation is misconfigured on this server", false, e);
        }
        if (code == HTTP_TOO_MANY_REQUESTS) {
            return new AiUnavailableException("The AI service is busy; please try again shortly", true, e);
        }
        log.error("Gemini API error {} ({})", code, e.status());
        return new AiUnavailableException("The AI service returned an error", false, e);
    }

    /** Gemini uses the role "model" where Claude uses "assistant". */
    static List<Content> toContents(AiPrompt prompt) {
        List<Content> contents = new ArrayList<>();
        for (AiPrompt.Message message : prompt.messages()) {
            String role = message.role() == AiPrompt.Role.USER ? ROLE_USER : ROLE_MODEL;
            contents.add(Content.builder().role(role).parts(Part.fromText(message.content())).build());
        }
        return contents;
    }

    /** Copies the schema without keywords Gemini rejects. Content rules are still enforced by the validator. */
    Map<String, Object> toSchemaMap(JsonNode schema) {
        return objectMapper.convertValue(sanitize(schema.deepCopy()), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    }

    static JsonNode sanitize(JsonNode node) {
        if (node instanceof ObjectNode object) {
            UNSUPPORTED_SCHEMA_KEYS.forEach(object::remove);
            for (Iterator<Map.Entry<String, JsonNode>> it = object.fields(); it.hasNext(); ) {
                sanitize(it.next().getValue());
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(GeminiClient::sanitize);
        }
        return node;
    }

    static boolean isBadRequest(ClientException e) {
        return e.code() == HTTP_BAD_REQUEST;
    }

    @SuppressWarnings("unused")
    private static String describe(Candidate candidate) {
        return candidate.finishReason().map(Object::toString).orElse("UNKNOWN");
    }
}
