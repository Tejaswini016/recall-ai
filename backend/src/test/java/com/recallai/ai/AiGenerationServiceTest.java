package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recallai.AbstractIntegrationTest;
import com.recallai.exception.ApiException;
import com.recallai.repository.AiCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Exercises the full generation pipeline against PostgreSQL with the scripted fake client. */
class AiGenerationServiceTest extends AbstractIntegrationTest {

    private static final String MATERIAL = "The mitochondrion is the organelle that produces most of a cell's ATP.";
    private static final String CARDS = """
            {"cards": [{"question": "Which organelle produces most ATP?", "answer": "The mitochondrion.",
                        "explanation": "It runs cellular respiration.", "topic": "Cell energy", "tags": ["organelles"]}]}
            """;
    private static final String QUIZ = """
            {"questions": [{"question": "Which organelle produces most ATP?",
                            "options": ["Nucleus", "Mitochondrion", "Ribosome", "Golgi apparatus"],
                            "correctAnswer": 1, "explanation": "Mitochondria carry out respiration."}]}
            """;

    @Autowired
    private AiGenerationService generationService;
    @Autowired
    private FakeClaudeClient fakeClaudeClient;
    @Autowired
    private AiCacheRepository cacheRepository;

    @BeforeEach
    void resetFake() {
        fakeClaudeClient.reset();
    }

    @Test
    void firstRequestCallsTheModelAndCachesTheValidatedResult() {
        fakeClaudeClient.reply(CARDS);

        AiGenerationResult<GeneratedFlashcard> result = generationService.generateFlashcards(MATERIAL, 10);

        assertThat(result.cached()).isFalse();
        assertThat(result.retries()).isZero();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).tags()).containsExactly("organelles");
        assertThat(fakeClaudeClient.calls()).isEqualTo(1);
        assertThat(cacheRepository.count()).isEqualTo(1);
        var entry = cacheRepository.findAll().get(0);
        assertThat(entry.getOperationType()).isEqualTo("FLASHCARDS");
        assertThat(entry.getModel()).isEqualTo("test-model");
        assertThat(entry.getPromptVersion()).isEqualTo(FlashcardPromptBuilder.VERSION);
        assertThat(entry.getContentHash()).hasSize(64);
    }

    @Test
    void equivalentMaterialIsServedFromCacheWithoutCallingTheModel() {
        fakeClaudeClient.reply(CARDS);
        generationService.generateFlashcards(MATERIAL, 10);

        AiGenerationResult<GeneratedFlashcard> second = generationService.generateFlashcards(
                "  the MITOCHONDRION is the organelle   that produces most of a cell's ATP.\n", 10);

        assertThat(second.cached()).isTrue();
        assertThat(second.items()).isEqualTo(generationService.generateFlashcards(MATERIAL, 10).items());
        assertThat(fakeClaudeClient.calls()).isEqualTo(1);
    }

    @Test
    void differentCountOrOperationIsADifferentCacheEntry() {
        fakeClaudeClient.reply(CARDS).reply(CARDS).reply(QUIZ);

        generationService.generateFlashcards(MATERIAL, 10);
        generationService.generateFlashcards(MATERIAL, 5);
        AiGenerationResult<GeneratedQuizQuestion> quiz = generationService.generateQuiz(MATERIAL, 10);

        assertThat(fakeClaudeClient.calls()).isEqualTo(3);
        assertThat(cacheRepository.count()).isEqualTo(3);
        assertThat(quiz.items().get(0).correctAnswer()).isEqualTo(1);
    }

    @Test
    void invalidOutputIsRetriedOnceAndNeverCachedWhenStillInvalid() {
        fakeClaudeClient.reply("not json").reply("{\"cards\": []}");

        assertThatThrownBy(() -> generationService.generateFlashcards(MATERIAL, 10))
                .isInstanceOf(AiInvalidResponseException.class);

        assertThat(fakeClaudeClient.calls()).isEqualTo(2);
        assertThat(cacheRepository.count()).isZero();
    }

    @Test
    void correctedOutputIsCachedAndReportsTheRetry() {
        fakeClaudeClient.reply("oops").reply(CARDS);

        AiGenerationResult<GeneratedFlashcard> result = generationService.generateFlashcards(MATERIAL, 10);

        assertThat(result.retries()).isEqualTo(1);
        assertThat(cacheRepository.count()).isEqualTo(1);
    }

    @Test
    void modelOutageSurfacesAsAiError() {
        fakeClaudeClient.fail(new AiUnavailableException("Claude is overloaded", false));

        assertThatThrownBy(() -> generationService.generateFlashcards(MATERIAL, 10))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("overloaded");
        assertThat(cacheRepository.count()).isZero();
    }

    @Test
    void emptyOrOversizedMaterialIsRejectedBeforeCallingTheModel() {
        assertThatThrownBy(() -> generationService.generateFlashcards("   ", 10))
                .isInstanceOf(ApiException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> generationService.generateFlashcards("x".repeat(20_000), 10))
                .isInstanceOf(ApiException.class).hasMessageContaining("split it into chunks");
        assertThat(fakeClaudeClient.calls()).isZero();
    }

    @Test
    void requestedCountIsClampedToTheConfiguredMaximum() {
        fakeClaudeClient.reply(CARDS);

        generationService.generateFlashcards(MATERIAL, 500);

        assertThat(fakeClaudeClient.prompts().get(0).messages().get(0).content()).contains("up to 30 flashcards");
    }
}
