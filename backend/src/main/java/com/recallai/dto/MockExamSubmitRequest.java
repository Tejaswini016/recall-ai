package com.recallai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param answers one entry per answered question; questions left out count as skipped
 */
public record MockExamSubmitRequest(@NotNull @Size(max = 100) List<@Valid Answer> answers) {

    public record Answer(
            @NotNull Long questionId,
            @Min(0) @Max(3) Integer selectedOption,
            @Size(max = 500) String answerText) {
    }
}
