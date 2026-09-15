package com.recallai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A practice quiz on one topic, built from the user's own cards that carry it.
 *
 * @param count desired number of questions (default 5)
 */
public record GenerateTopicQuizRequest(
        @NotBlank @Size(max = 150) String topic,
        @Min(1) @Max(GenerateQuizRequest.MAX_COUNT) Integer count) {
}
