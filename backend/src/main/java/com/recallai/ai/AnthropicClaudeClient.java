package com.recallai.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Claude API access through the official Java SDK. Translates {@link AiPrompt} into a
 * Messages request with a JSON-schema output constraint, and every failure mode into
 * {@link AiUnavailableException} with an accurate retryable flag. Study content is never
 * logged here.
 */
@Component
@ConditionalOnProperty(prefix = "recallai.ai", name = "demo-mode", havingValue = "false", matchIfMissing = true)
public class AnthropicClaudeClient implements ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClaudeClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    /** The SDK already retries 429/5xx; the retry service adds one more layer, so keep this small. */
    private static final int SDK_MAX_RETRIES = 2;

    private final AiProperties properties;
    private final AnthropicClient client;

    public AnthropicClaudeClient(AiProperties properties) {
        this.properties = properties;
        if (properties.hasApiKey()) {
            this.client = AnthropicOkHttpClient.builder()
                    .apiKey(properties.apiKey())
                    .timeout(REQUEST_TIMEOUT)
                    .maxRetries(SDK_MAX_RETRIES)
                    .build();
            log.info("Claude client configured for model {}", properties.model());
        } else {
            this.client = null;
            log.warn("CLAUDE_API_KEY is not set; AI generation endpoints will return an error");
        }
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        if (client == null) {
            throw new AiUnavailableException("AI generation is not configured on this server", false);
        }
        MessageCreateParams params = toParams(prompt);
        long started = System.nanoTime();
        Message message;
        try {
            message = client.messages().create(params);
        } catch (RateLimitException e) {
            throw new AiUnavailableException("The AI service is busy; please try again shortly", true, e);
        } catch (InternalServerException e) {
            throw new AiUnavailableException("The AI service is temporarily unavailable", true, e);
        } catch (UnauthorizedException e) {
            log.error("Claude API rejected the configured API key");
            throw new AiUnavailableException("AI generation is misconfigured on this server", false, e);
        } catch (AnthropicServiceException e) {
            log.error("Claude API error {} ({})", e.statusCode(), e.errorType().map(Object::toString).orElse("unknown"));
            throw new AiUnavailableException("The AI service returned an error", false, e);
        } catch (AnthropicIoException e) {
            throw new AiUnavailableException("Could not reach the AI service", true, e);
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        StopReason stopReason = message.stopReason().orElse(StopReason.END_TURN);
        long inputTokens = message.usage().inputTokens();
        long outputTokens = message.usage().outputTokens();
        log.info("Claude {} call finished in {} ms: stop={} in={} out={}",
                prompt.operation(), elapsedMs, stopReason.asString(), inputTokens, outputTokens);

        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new AiUnavailableException("The AI declined to process this material", false);
        }
        String text = message.content().stream()
                .map(ContentBlock::text)
                .flatMap(java.util.Optional::stream)
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return new AiCompletion(text, StopReason.MAX_TOKENS.equals(stopReason), inputTokens, outputTokens);
    }

    private MessageCreateParams toParams(AiPrompt prompt) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(prompt.system())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .format(JsonOutputFormat.builder().schema(toSchema(prompt.outputSchema())).build())
                        .build());
        for (AiPrompt.Message message : prompt.messages()) {
            if (message.role() == AiPrompt.Role.USER) {
                builder.addUserMessage(message.content());
            } else {
                builder.addAssistantMessage(message.content());
            }
        }
        return builder.build();
    }

    private static JsonOutputFormat.Schema toSchema(JsonNode schema) {
        JsonOutputFormat.Schema.Builder builder = JsonOutputFormat.Schema.builder();
        for (Iterator<Map.Entry<String, JsonNode>> it = schema.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            builder.putAdditionalProperty(field.getKey(), JsonValue.fromJsonNode(field.getValue()));
        }
        return builder.build();
    }
}
