package com.recallai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param quality recall quality: 0 = complete blackout … 5 = perfect, effortless recall */
public record ReviewRequest(@NotNull @Min(0) @Max(5) Integer quality) {
}
