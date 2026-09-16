package com.recallai.dto;

import com.recallai.entity.StudyPlanStatus;
import jakarta.validation.constraints.NotNull;

public record UpdatePlanStatusRequest(@NotNull StudyPlanStatus status) {
}
