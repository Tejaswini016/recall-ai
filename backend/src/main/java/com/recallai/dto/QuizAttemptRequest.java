package com.recallai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param answers         one entry per answered question; questions left out count as skipped
 * @param durationSeconds optional wall-clock time the attempt took, as measured by the client
 */
public record QuizAttemptRequest(
        @NotNull @Size(max = 100) List<@Valid AnswerSubmission> answers,
        @Min(0) @Max(86_400) Integer durationSeconds) {

    public record AnswerSubmission(
            @NotNull Long questionId,
            @Min(0) @Max(3) Integer selectedAnswer) {
    }
}
