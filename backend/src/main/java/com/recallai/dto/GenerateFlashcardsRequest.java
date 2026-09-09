package com.recallai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param count desired number of cards; the service may return fewer if the material is thin
 */
public record GenerateFlashcardsRequest(
        @NotNull Long deckId,
        @NotBlank String text,
        @Min(1) @Max(MAX_COUNT) Integer count) {

    public static final int MAX_COUNT = 60;
}
