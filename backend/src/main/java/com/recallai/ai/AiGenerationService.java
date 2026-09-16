package com.recallai.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade for structured AI generation: cache lookup, prompt, call with retries, validation,
 * cache store. Callers receive validated domain objects or a controlled {@link ApiException};
 * they never see raw model output.
 */
@Service
public class AiGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationService.class);
    private static final TypeReference<List<GeneratedFlashcard>> FLASHCARD_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<GeneratedQuizQuestion>> QUIZ_LIST = new TypeReference<>() {
    };

    private final PromptService promptService;
    private final AiRetryService retryService;
    private final AiResponseValidator validator;
    private final AiCacheService cacheService;
    private final AiProperties properties;

    public AiGenerationService(PromptService promptService, AiRetryService retryService,
                               AiResponseValidator validator, AiCacheService cacheService, AiProperties properties) {
        this.promptService = promptService;
        this.retryService = retryService;
        this.validator = validator;
        this.cacheService = cacheService;
        this.properties = properties;
    }

    public AiGenerationResult<GeneratedFlashcard> generateFlashcards(String material, int requestedCards) {
        int count = clamp(requestedCards, properties.maxCards());
        checkMaterial(material);
        AiCacheService.CacheKey key = AiCacheService.CacheKey.of(material, "count=" + count, AiOperation.FLASHCARDS,
                properties.effectiveModel(), promptService.promptVersion(AiOperation.FLASHCARDS));

        Optional<List<GeneratedFlashcard>> cached = cacheService.lookup(key, FLASHCARD_LIST);
        if (cached.isPresent()) {
            return new AiGenerationResult<>(cached.get(), true, 0);
        }

        AiRetryService.Attempt<List<GeneratedFlashcard>> attempt = retryService.execute(
                promptService.flashcards(material, count), text -> validator.validateFlashcards(text, count));
        cacheService.store(key, attempt.value());
        log.info("Generated {} flashcards", attempt.value().size());
        return new AiGenerationResult<>(attempt.value(), false, attempt.correctiveRetries());
    }

    public AiGenerationResult<GeneratedQuizQuestion> generateQuiz(String material, int requestedQuestions) {
        int count = clamp(requestedQuestions, properties.maxQuizQuestions());
        checkMaterial(material);
        AiCacheService.CacheKey key = AiCacheService.CacheKey.of(material, "count=" + count, AiOperation.QUIZ,
                properties.effectiveModel(), promptService.promptVersion(AiOperation.QUIZ));

        Optional<List<GeneratedQuizQuestion>> cached = cacheService.lookup(key, QUIZ_LIST);
        if (cached.isPresent()) {
            return new AiGenerationResult<>(cached.get(), true, 0);
        }

        AiRetryService.Attempt<List<GeneratedQuizQuestion>> attempt = retryService.execute(
                promptService.quiz(material, count), text -> validator.validateQuiz(text, count));
        cacheService.store(key, attempt.value());
        log.info("Generated {} quiz questions", attempt.value().size());
        return new AiGenerationResult<>(attempt.value(), false, attempt.correctiveRetries());
    }

    /**
     * One corrective card for a mistake. Cached on the question, correct answer and explanation
     * plus the student's answer, since the card addresses that specific misunderstanding.
     */
    public AiGenerationResult<GeneratedFlashcard> generateMistakeCard(MistakeContext mistake) {
        String material = mistake.question() + "\n" + mistake.correctAnswer() + "\n"
                + (mistake.explanation() == null ? "" : mistake.explanation());
        String parameters = "given=" + (mistake.givenAnswer() == null ? "" : mistake.givenAnswer())
                + ";topic=" + (mistake.topic() == null ? "" : mistake.topic());
        AiCacheService.CacheKey key = AiCacheService.CacheKey.of(material, parameters, AiOperation.MISTAKE_CARD,
                properties.effectiveModel(), promptService.promptVersion(AiOperation.MISTAKE_CARD));
        Optional<List<GeneratedFlashcard>> cached = cacheService.lookup(key, FLASHCARD_LIST);
        if (cached.isPresent() && !cached.get().isEmpty()) {
            return new AiGenerationResult<>(cached.get(), true, 0);
        }
        AiRetryService.Attempt<GeneratedFlashcard> attempt = retryService.execute(
                promptService.mistakeCard(mistake), validator::validateMistakeCard);
        List<GeneratedFlashcard> items = List.of(attempt.value());
        cacheService.store(key, items);
        log.info("Generated a corrective flashcard for a mistake");
        return new AiGenerationResult<>(items, false, attempt.correctiveRetries());
    }

    private void checkMaterial(String material) {
        if (material == null || material.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Study material is empty");
        }
        if (material.length() > properties.maxInputChars()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Study material exceeds " + properties.maxInputChars()
                    + " characters per request; split it into chunks");
        }
    }

    private static int clamp(int requested, int max) {
        return Math.max(1, Math.min(requested, max));
    }
}
