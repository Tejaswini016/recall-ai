package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Groq access over its OpenAI-compatible chat-completions API, selected with
 * {@code AI_PROVIDER=groq}. Groq has no official Java SDK, so this uses Spring's
 * {@link RestClient}. Because the base URL is configurable ({@code GROQ_BASE_URL}), the same
 * client also drives any other OpenAI-compatible server, for example a local Ollama.
 *
 * <p>JSON output is requested with {@code response_format: json_object}; the schema itself is
 * enforced by {@link AiResponseValidator} exactly as for the other providers. Study content is
 * never logged.
 */
@Component
@ConditionalOnExpression("'${recallai.ai.demo-mode:false}' != 'true' && '${recallai.ai.provider:anthropic}'.toLowerCase() == 'groq'")
public class GroqClient implements ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    static final String CHAT_COMPLETIONS = "/chat/completions";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GroqClient(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // Timeouts come from spring.http.client.* so tests can bind a mock server to the same builder.
        this.restClient = restClientBuilder.baseUrl(properties.groqBaseUrl()).build();
        if (properties.hasGroqApiKey()) {
            log.info("Groq client configured for model {} at {}", properties.groqModel(), properties.groqBaseUrl());
        } else {
            log.warn("GROQ_API_KEY is not set; AI generation endpoints will return an error");
        }
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        if (!properties.hasGroqApiKey()) {
            throw new AiUnavailableException("AI generation is not configured on this server", false);
        }
        ObjectNode body = toRequestBody(prompt, properties.groqModel(), properties.maxTokens());
        long started = System.nanoTime();
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(CHAT_COMPLETIONS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.groqApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw translateClientError(e.getStatusCode(), safeMessage(e.getResponseBodyAsString()));
        } catch (HttpServerErrorException e) {
            throw new AiUnavailableException("The AI service is temporarily unavailable", true, e);
        } catch (ResourceAccessException e) {
            throw new AiUnavailableException("Could not reach the AI service", true, e);
        }

        AiCompletion completion = parseResponse(response);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        log.info("Groq {} call finished in {} ms: truncated={} in={} out={}",
                prompt.operation(), elapsedMs, completion.truncated(), completion.inputTokens(), completion.outputTokens());
        return completion;
    }

    /** OpenAI chat-completions shape: system + turns, JSON mode, deterministic-ish temperature. */
    ObjectNode toRequestBody(AiPrompt prompt, String model, long maxTokens) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", maxTokens);
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.system());
        for (AiPrompt.Message message : prompt.messages()) {
            String role = message.role() == AiPrompt.Role.USER ? "user" : "assistant";
            messages.addObject().put("role", role).put("content", message.content());
        }
        return body;
    }

    static AiCompletion parseResponse(JsonNode response) {
        JsonNode choice = response == null ? null : response.path("choices").path(0);
        if (choice == null || choice.isMissingNode()) {
            throw new AiUnavailableException("The AI service returned an empty response", true);
        }
        String text = choice.path("message").path("content").asText("");
        String finish = choice.path("finish_reason").asText("").toLowerCase(Locale.ROOT);
        if ("content_filter".equals(finish)) {
            throw new AiUnavailableException("The AI declined to process this material", false);
        }
        long in = response.path("usage").path("prompt_tokens").asLong(0);
        long out = response.path("usage").path("completion_tokens").asLong(0);
        return new AiCompletion(text, "length".equals(finish), in, out);
    }

    static AiUnavailableException translateClientError(HttpStatusCode status, String detail) {
        int code = status.value();
        if (code == 401 || code == 403) {
            log.error("Groq API rejected the configured API key (HTTP {})", code);
            return new AiUnavailableException("AI generation is misconfigured on this server", false);
        }
        if (code == 429) {
            return new AiUnavailableException("The AI service is busy; please try again shortly", true);
        }
        if (code == 404) {
            log.error("Groq model is not available: {}", detail);
            return new AiUnavailableException("The configured AI model is not available; set GROQ_MODEL to a current model", false);
        }
        log.error("Groq API error {}: {}", code, detail);
        return new AiUnavailableException("The AI service returned an error", false);
    }

    /** Keeps the provider's error text short and free of anything resembling a key. */
    private static String safeMessage(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ");
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }
}
