package com.recallai.dto;

/**
 * @param aiGenerated true when the model wrote the card; false when it was built directly from the
 *                    stored question, correct answer and explanation because the model was
 *                    unavailable or returned something invalid
 */
public record MistakeFlashcardResponse(MistakeResponse mistake, CardResponse card, boolean aiGenerated) {
}
