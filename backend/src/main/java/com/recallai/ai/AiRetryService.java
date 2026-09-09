package com.recallai.ai;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The AI failure strategy in one place:
 *
 * <ol>
 *   <li>Call the model.</li>
 *   <li>Validate the reply.</li>
 *   <li>If invalid, re-ask with the reply and the exact problems quoted back
 *       ({@code validationRetries} times).</li>
 *   <li>If a transient transport failure occurs, wait and retry
 *       ({@code transportRetries} times).</li>
 *   <li>Otherwise surface a controlled error; nothing invalid ever escapes.</li>
 * </ol>
 */
@Service
public class AiRetryService {

    private static final Logger log = LoggerFactory.getLogger(AiRetryService.class);
    private static final long TRANSPORT_BACKOFF_MS = 1500;

    private final ClaudeClient claudeClient;
    private final PromptService promptService;
    private final AiProperties properties;
    private final Sleeper sleeper;

    @Autowired
    public AiRetryService(ClaudeClient claudeClient, PromptService promptService, AiProperties properties) {
        this(claudeClient, promptService, properties, Sleeper.REAL);
    }

    AiRetryService(ClaudeClient claudeClient, PromptService promptService, AiProperties properties, Sleeper sleeper) {
        this.claudeClient = claudeClient;
        this.promptService = promptService;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /**
     * @param parser turns raw text into validated items or throws {@link AiInvalidResponseException}
     * @return the validated items and how many corrective retries were needed
     */
    public <T> Attempt<T> execute(AiPrompt prompt, Function<String, T> parser) {
        AiPrompt current = prompt;
        int correctiveRetries = 0;
        while (true) {
            AiCompletion completion = completeWithTransportRetries(current);
            try {
                if (completion.truncated()) {
                    throw new AiInvalidResponseException(java.util.List.of(
                            "response was cut off by the output limit; return fewer, shorter items"));
                }
                T parsed = parser.apply(completion.text());
                log.info("AI {} succeeded after {} corrective retries ({} in / {} out tokens)",
                        prompt.operation(), correctiveRetries, completion.inputTokens(), completion.outputTokens());
                return new Attempt<>(parsed, correctiveRetries);
            } catch (AiInvalidResponseException invalid) {
                if (correctiveRetries >= properties.validationRetries()) {
                    log.warn("AI {} response still invalid after {} corrective retries: {}",
                            prompt.operation(), correctiveRetries, invalid.getProblems());
                    throw invalid;
                }
                correctiveRetries++;
                log.info("AI {} response invalid ({}); corrective retry {} of {}",
                        prompt.operation(), invalid.getProblems(), correctiveRetries, properties.validationRetries());
                current = promptService.corrective(current, completion.text(), invalid.getProblems());
            }
        }
    }

    private AiCompletion completeWithTransportRetries(AiPrompt prompt) {
        int attempts = 0;
        while (true) {
            try {
                return claudeClient.complete(prompt);
            } catch (AiUnavailableException e) {
                if (!e.isRetryable() || attempts >= properties.transportRetries()) {
                    log.warn("AI {} call failed after {} transport retries: {}",
                            prompt.operation(), attempts, e.getMessage());
                    throw e;
                }
                attempts++;
                log.info("AI {} transient failure ({}); transport retry {} of {}",
                        prompt.operation(), e.getMessage(), attempts, properties.transportRetries());
                sleeper.sleep(TRANSPORT_BACKOFF_MS * attempts);
            }
        }
    }

    public record Attempt<T>(T value, int correctiveRetries) {
    }

    /** Injectable delay so tests do not actually wait. */
    interface Sleeper {
        Sleeper REAL = millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        void sleep(long millis);
    }
}
