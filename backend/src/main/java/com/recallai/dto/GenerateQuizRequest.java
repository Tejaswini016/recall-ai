package com.recallai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param text  optional study material; when absent the quiz is built from the deck's cards
 * @param count desired number of questions (default 10)
 */
public record GenerateQuizRequest(
        @NotNull Long deckId,
        @Size(max = 200) String title,
        String text,
        @Min(1) @Max(MAX_COUNT) Integer count) {

    public static final int MAX_COUNT = 30;
}
