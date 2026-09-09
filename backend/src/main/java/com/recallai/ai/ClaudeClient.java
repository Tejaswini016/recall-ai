package com.recallai.ai;

/**
 * The single seam between the application and the Claude API. Production uses the SDK-backed
 * implementation; tests substitute a scripted fake so no test needs network access or a key.
 */
public interface ClaudeClient {

    /**
     * @throws AiUnavailableException when the call could not be completed (network, rate limit,
     *                                server error, refusal, configuration)
     */
    AiCompletion complete(AiPrompt prompt);
}
