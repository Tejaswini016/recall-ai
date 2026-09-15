package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiRetryServiceTest {

    private static final String VALID = """
            {"cards": [{"question": "Q", "answer": "A", "explanation": "", "topic": "T", "tags": []}]}
            """;

    private final FakeClaudeClient client = new FakeClaudeClient();
    private final List<Long> sleeps = new ArrayList<>();
    private AiRetryService retryService;
    private AiResponseValidator validator;
    private AiPrompt prompt;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        PromptService promptService = new PromptService(new FlashcardPromptBuilder(mapper), new QuizPromptBuilder(mapper));
        validator = new AiResponseValidator(mapper);
        retryService = new AiRetryService(client, promptService, properties(1, 1), sleeps::add);
        prompt = promptService.flashcards("Some material", 5);
    }

    @Test
    void validFirstReplyNeedsNoRetry() {
        client.reply(VALID);

        AiRetryService.Attempt<List<GeneratedFlashcard>> attempt =
                retryService.execute(prompt, text -> validator.validateFlashcards(text, 5));

        assertThat(attempt.value()).hasSize(1);
        assertThat(attempt.correctiveRetries()).isZero();
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void invalidReplyTriggersOneCorrectiveRetryThatQuotesTheProblems() {
        client.reply("Sure, here are some cards!").reply(VALID);

        AiRetryService.Attempt<List<GeneratedFlashcard>> attempt =
                retryService.execute(prompt, text -> validator.validateFlashcards(text, 5));

        assertThat(attempt.correctiveRetries()).isEqualTo(1);
        assertThat(client.calls()).isEqualTo(2);
        AiPrompt corrective = client.prompts().get(1);
        assertThat(corrective.messages()).hasSize(3);
        assertThat(corrective.messages().get(1).role()).isEqualTo(AiPrompt.Role.ASSISTANT);
        assertThat(corrective.messages().get(1).content()).isEqualTo("Sure, here are some cards!");
        assertThat(corrective.messages().get(2).role()).isEqualTo(AiPrompt.Role.USER);
        assertThat(corrective.messages().get(2).content())
                .contains("rejected")
                .contains("not valid JSON")
                .contains("ONLY a single JSON object");
        assertThat(corrective.system()).isEqualTo(prompt.system());
        assertThat(corrective.outputSchema()).isEqualTo(prompt.outputSchema());
    }

    @Test
    void twoInvalidRepliesEndInAControlledError() {
        client.reply("{\"cards\": []}").reply("{\"cards\": [{\"question\": \"\"}]}");

        assertThatThrownBy(() -> retryService.execute(prompt, text -> validator.validateFlashcards(text, 5)))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("cards[0].question is blank");
        assertThat(client.calls()).isEqualTo(2);
    }

    @Test
    void truncatedReplyIsTreatedAsInvalid() {
        client.reply(new AiCompletion(VALID, true, 10, 10)).reply(VALID);

        AiRetryService.Attempt<List<GeneratedFlashcard>> attempt =
                retryService.execute(prompt, text -> validator.validateFlashcards(text, 5));

        assertThat(attempt.correctiveRetries()).isEqualTo(1);
        assertThat(client.prompts().get(1).messages().get(2).content()).contains("cut off");
    }

    @Test
    void transientTransportFailureIsRetriedAfterABackoff() {
        client.fail(new AiUnavailableException("overloaded", true)).reply(VALID);

        AiRetryService.Attempt<List<GeneratedFlashcard>> attempt =
                retryService.execute(prompt, text -> validator.validateFlashcards(text, 5));

        assertThat(attempt.correctiveRetries()).isZero();
        assertThat(client.calls()).isEqualTo(2);
        assertThat(sleeps).containsExactly(1500L);
    }

    @Test
    void repeatedTransportFailureSurfacesTheError() {
        client.fail(new AiUnavailableException("overloaded", true)).fail(new AiUnavailableException("still down", true));

        assertThatThrownBy(() -> retryService.execute(prompt, text -> validator.validateFlashcards(text, 5)))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("still down");
        assertThat(client.calls()).isEqualTo(2);
    }

    @Test
    void nonRetryableFailureIsNotRetried() {
        client.fail(new AiUnavailableException("invalid api key", false));

        assertThatThrownBy(() -> retryService.execute(prompt, text -> validator.validateFlashcards(text, 5)))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(client.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void zeroRetriesMeansOneAttemptOnly() {
        retryService = new AiRetryService(client, new PromptService(new FlashcardPromptBuilder(new ObjectMapper()),
                new QuizPromptBuilder(new ObjectMapper())), properties(0, 0), sleeps::add);
        client.reply("garbage");

        assertThatThrownBy(() -> retryService.execute(prompt, text -> validator.validateFlashcards(text, 5)))
                .isInstanceOf(AiInvalidResponseException.class);
        assertThat(client.calls()).isEqualTo(1);
    }

    static AiProperties properties(int validationRetries, int transportRetries) {
        return new AiProperties("key", "test-model", "medium", 4096, 12000, 30, 15,
                validationRetries, transportRetries, 20, false, AiProvider.ANTHROPIC, "", "gemini-3.6-flash", "", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1");
    }
}
