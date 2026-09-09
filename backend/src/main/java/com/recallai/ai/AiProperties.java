package com.recallai.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code recallai.ai.*}.
 *
 * @param apiKey               Claude API key; may be blank in environments that never call the model
 * @param model                model id, part of every cache key so a model change never serves stale output
 * @param effort               reasoning effort for generation requests (low, medium, high)
 * @param maxTokens            output token ceiling per request
 * @param maxInputChars        largest study-material payload a single request may carry; callers chunk above this
 * @param maxCards             upper bound on flashcards per request
 * @param maxQuizQuestions     upper bound on quiz questions per request
 * @param validationRetries    corrective re-asks after an invalid response before giving up
 * @param transportRetries     extra attempts after a transient API failure (on top of the SDK's own retries)
 * @param rateLimitPerHour     AI generation requests allowed per user per hour
 */
@Validated
@ConfigurationProperties(prefix = "recallai.ai")
public record AiProperties(
        String apiKey,
        @NotBlank String model,
        @NotBlank String effort,
        @Min(256) long maxTokens,
        @Min(500) int maxInputChars,
        @Min(1) int maxCards,
        @Min(1) int maxQuizQuestions,
        @Min(0) int validationRetries,
        @Min(0) int transportRetries,
        @Min(1) int rateLimitPerHour) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
