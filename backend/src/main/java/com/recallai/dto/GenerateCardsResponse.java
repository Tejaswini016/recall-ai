package com.recallai.dto;

import java.util.List;

/**
 * @param chunks        how many model-sized pieces the material was split into
 * @param cachedChunks  how many of those were served from the AI cache without a model call
 * @param retries       corrective re-asks that were needed across all chunks
 */
public record GenerateCardsResponse(
        Long deckId,
        int cardsCreated,
        int chunks,
        int cachedChunks,
        int retries,
        List<CardResponse> cards) {
}
