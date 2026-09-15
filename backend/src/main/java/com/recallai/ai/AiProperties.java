package com.recallai.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code recallai.ai.*}.
 *
 * @param apiKey               Claude API key; may be blank in environments that never call the model
 * @param model                Claude model id, part of every cache key so a model change never serves stale output
 * @param effort               reasoning effort for Claude generation requests (low, medium, high)
 * @param maxTokens            output token ceiling per request
 * @param maxInputChars        largest study-material payload a single request may carry; callers chunk above this
 * @param maxCards             upper bound on flashcards per request
 * @param maxQuizQuestions     upper bound on quiz questions per request
 * @param validationRetries    corrective re-asks after an invalid response before giving up
 * @param transportRetries     extra attempts after a transient API failure (on top of the SDK's own retries)
 * @param rateLimitPerHour     AI generation requests allowed per user per hour
 * @param demoMode             replace the hosted model with a local heuristic generator (no key, no cost, not AI)
 * @param provider             which hosted provider generates when demo mode is off
 * @param geminiApiKey         Google Gemini API key, used only when the provider is GEMINI
 * @param geminiModel          Gemini model id, part of the cache key when the provider is GEMINI
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
        @Min(1) int rateLimitPerHour,
        boolean demoMode,
        @NotNull AiProvider provider,
        String geminiApiKey,
        @NotBlank String geminiModel) {

    public static final String DEMO_MODEL = "demo";

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasGeminiApiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    /** Cache keys use this so demo, Gemini and Claude output never mix. */
    public String effectiveModel() {
        if (demoMode) {
            return DEMO_MODEL;
        }
        return provider == AiProvider.GEMINI ? geminiModel : model;
    }

    /** Human-readable provider for status responses and logs. */
    public String effectiveProvider() {
        return demoMode ? "demo" : provider.name().toLowerCase(Locale.ROOT);
    }

    public boolean generationAvailable() {
        if (demoMode) {
            return true;
        }
        return provider == AiProvider.GEMINI ? hasGeminiApiKey() : hasApiKey();
    }
}
