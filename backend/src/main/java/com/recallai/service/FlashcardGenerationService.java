package com.recallai.service;

import com.recallai.ai.AiGenerationResult;
import com.recallai.ai.AiGenerationService;
import com.recallai.ai.AiProperties;
import com.recallai.ai.GeneratedFlashcard;
import com.recallai.config.MaterialProperties;
import com.recallai.dto.CardRequest;
import com.recallai.dto.CardResponse;
import com.recallai.dto.GenerateCardsResponse;
import com.recallai.entity.CardOrigin;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns study material into persisted cards: extract, chunk, generate per chunk (each call
 * cached and validated independently), merge, de-duplicate, then save in one transaction.
 * Model calls happen outside any database transaction so slow generations never hold a
 * connection. If any chunk ultimately fails, nothing is saved; the chunks that succeeded
 * are already cached, so a retry only pays for the failed piece.
 */
@Service
public class FlashcardGenerationService {

    private static final Logger log = LoggerFactory.getLogger(FlashcardGenerationService.class);
    static final int DEFAULT_COUNT = 15;

    private final StudyMaterialExtractor extractor;
    private final AiGenerationService aiGenerationService;
    private final DeckService deckService;
    private final CardService cardService;
    private final AiProperties aiProperties;
    private final MaterialProperties materialProperties;

    public FlashcardGenerationService(StudyMaterialExtractor extractor, AiGenerationService aiGenerationService,
                                      DeckService deckService, CardService cardService, AiProperties aiProperties,
                                      MaterialProperties materialProperties) {
        this.extractor = extractor;
        this.aiGenerationService = aiGenerationService;
        this.deckService = deckService;
        this.cardService = cardService;
        this.aiProperties = aiProperties;
        this.materialProperties = materialProperties;
    }

    public GenerateCardsResponse generateFromText(Long userId, Long deckId, String text, Integer count) {
        deckService.getOwnedDeck(userId, deckId);
        return generate(userId, deckId, extractor.fromText(text), count);
    }

    public GenerateCardsResponse generateFromFile(Long userId, Long deckId, MultipartFile file, Integer count) {
        deckService.getOwnedDeck(userId, deckId);
        return generate(userId, deckId, extractor.fromFile(file), count);
    }

    private GenerateCardsResponse generate(Long userId, Long deckId, String material, Integer requestedCount) {
        if (material.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Study material is empty");
        }
        int count = requestedCount == null ? DEFAULT_COUNT : requestedCount;
        List<String> chunks = TextChunker.chunk(material, aiProperties.maxInputChars());
        if (chunks.size() > materialProperties.maxChunks()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Study material is too long for one generation ("
                    + chunks.size() + " parts; the maximum is " + materialProperties.maxChunks()
                    + "). Split it into smaller documents.");
        }

        List<Integer> shares = GenerationPlanner.shares(chunks, count, aiProperties.maxCards());

        List<GeneratedFlashcard> merged = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        int cachedChunks = 0;
        int retries = 0;
        for (int i = 0; i < chunks.size(); i++) {
            AiGenerationResult<GeneratedFlashcard> result =
                    aiGenerationService.generateFlashcards(chunks.get(i), shares.get(i));
            if (result.cached()) {
                cachedChunks++;
            }
            retries += result.retries();
            for (GeneratedFlashcard card : result.items()) {
                if (seenQuestions.add(card.question().toLowerCase(Locale.ROOT))) {
                    merged.add(card);
                }
            }
        }
        List<GeneratedFlashcard> selected = merged.size() > count ? merged.subList(0, count) : merged;

        List<CardResponse> saved = cardService.createAll(userId, deckId,
                selected.stream().map(FlashcardGenerationService::toCardRequest).toList(), CardOrigin.AI);
        log.info("User {} generated {} cards into deck {} from {} chunks ({} cached, {} retries)",
                userId, saved.size(), deckId, chunks.size(), cachedChunks, retries);
        return new GenerateCardsResponse(deckId, saved.size(), chunks.size(), cachedChunks, retries, saved);
    }

    private static CardRequest toCardRequest(GeneratedFlashcard card) {
        return new CardRequest(card.question(), card.answer(), card.explanation(), card.topic(), card.tags());
    }
}
