package com.recallai.ai;

/**
 * Raw result of one model call, before any validation.
 *
 * @param text         the model's text output; never trusted until validated
 * @param truncated    true if generation stopped because the output limit was hit
 * @param inputTokens  usage for cost logging
 * @param outputTokens usage for cost logging
 */
public record AiCompletion(String text, boolean truncated, long inputTokens, long outputTokens) {

    public static AiCompletion ofText(String text) {
        return new AiCompletion(text, false, 0, 0);
    }
}
