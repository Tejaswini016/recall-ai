package com.recallai.service;

import com.recallai.ai.AiGenerationService;
import com.recallai.ai.AiInvalidResponseException;
import com.recallai.ai.AiProperties;
import com.recallai.ai.AiUnavailableException;
import com.recallai.ai.GeneratedFlashcard;
import com.recallai.ai.MistakeContext;
import com.recallai.dto.MistakeFlashcardResponse;
import com.recallai.entity.Deck;
import com.recallai.entity.Mistake;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * "Review this mistake": asks the model for a corrective flashcard, validates it, and stores it
 * so SM-2 schedules it from today. The model call runs outside any transaction. If the model is
 * not configured, unavailable, or returns something invalid even after the corrective retry, the
 * card is built deterministically from the stored question, correct answer and explanation, so
 * the student is never left without a card.
 */
@Service
public class MistakeFlashcardService {

    private static final Logger log = LoggerFactory.getLogger(MistakeFlashcardService.class);

    private final MistakeService mistakeService;
    private final AiGenerationService aiGenerationService;
    private final AiProperties aiProperties;

    public MistakeFlashcardService(MistakeService mistakeService, AiGenerationService aiGenerationService,
                                   AiProperties aiProperties) {
        this.mistakeService = mistakeService;
        this.aiGenerationService = aiGenerationService;
        this.aiProperties = aiProperties;
    }

    public MistakeFlashcardResponse convert(Long userId, Long mistakeId, Long requestedDeckId) {
        Mistake mistake = mistakeService.getOwned(userId, mistakeId);
        Deck deck = mistakeService.deckForFlashcard(userId, mistake, requestedDeckId);
        MistakeContext context = new MistakeContext(mistake.getQuestion(), mistake.getGivenAnswer(),
                mistake.getCorrectAnswer(), mistake.getExplanation(), mistake.getTopic());

        GeneratedFlashcard card = null;
        if (aiProperties.generationAvailable()) {
            try {
                card = aiGenerationService.generateMistakeCard(context).items().get(0);
            } catch (AiUnavailableException | AiInvalidResponseException e) {
                log.warn("Mistake {}: model could not produce a card ({}); using the deterministic fallback",
                        mistakeId, e.getMessage());
            }
        }
        boolean aiGenerated = card != null;
        if (card == null) {
            card = fallbackCard(context);
        }
        return mistakeService.persistFlashcard(userId, mistakeId, deck.getId(), card, aiGenerated);
    }

    /** A plain but correct card from what the quiz already knew. Never wrong, just less explanatory. */
    static GeneratedFlashcard fallbackCard(MistakeContext mistake) {
        StringBuilder explanation = new StringBuilder();
        if (mistake.explanation() != null && !mistake.explanation().isBlank()) {
            explanation.append(mistake.explanation().strip());
        }
        if (explanation.length() > 0) {
            explanation.append(' ');
        }
        explanation.append(mistake.givenAnswer() == null
                ? "You skipped this question last time."
                : "You answered \"" + mistake.givenAnswer().strip() + "\" last time.");
        return new GeneratedFlashcard(mistake.question().strip(), mistake.correctAnswer().strip(),
                explanation.toString(), mistake.topic(), List.of(MistakeService.MISTAKE_TAG));
    }
}
