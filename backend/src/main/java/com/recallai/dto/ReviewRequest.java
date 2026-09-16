package com.recallai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param quality recall quality: 0 = complete blackout … 5 = perfect, effortless recall */
/**
 * @param responseMs optional time from seeing the question to revealing the answer, measured by the client
 */
public record ReviewRequest(@NotNull @Min(0) @Max(5) Integer quality,
                            @Min(0) @Max(3_600_000) Integer responseMs) {
}
