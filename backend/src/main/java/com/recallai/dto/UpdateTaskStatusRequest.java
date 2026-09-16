package com.recallai.dto;

import com.recallai.entity.StudyTaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(@NotNull StudyTaskStatus status) {
}
