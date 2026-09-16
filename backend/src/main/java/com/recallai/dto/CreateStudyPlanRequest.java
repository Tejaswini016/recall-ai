package com.recallai.dto;

import com.recallai.entity.KnowledgeLevel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * @param preferredDays ISO weekday numbers (Monday = 1 ... Sunday = 7); every day when omitted
 */
public record CreateStudyPlanRequest(
        @NotBlank @Size(max = 150) String examName,
        @NotNull @Future LocalDate examDate,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 150) String> topics,
        @NotNull KnowledgeLevel knowledgeLevel,
        @NotNull @Min(10) @Max(720) Integer minutesPerDay,
        @Size(max = 7) List<@Min(1) @Max(7) Integer> preferredDays) {
}
