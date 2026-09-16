package com.recallai.dto;

/**
 * @param deckId deck to put the new card in; defaults to the deck the mistake came from
 */
public record ConvertMistakeRequest(Long deckId) {
}
