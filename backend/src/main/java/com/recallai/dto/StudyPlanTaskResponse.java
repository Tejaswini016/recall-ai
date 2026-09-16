package com.recallai.dto;

import com.recallai.entity.StudyPlanTask;
import com.recallai.entity.StudyTaskStatus;
import com.recallai.entity.StudyTaskType;
import java.time.Instant;
import java.time.LocalDate;

public record StudyPlanTaskResponse(
        Long id,
        Long planId,
        String examName,
        LocalDate date,
        int order,
        StudyTaskType type,
        String topic,
        String title,
        String description,
        int minutes,
        StudyTaskStatus status,
        Instant completedAt) {

    public static StudyPlanTaskResponse from(StudyPlanTask task) {
        return new StudyPlanTaskResponse(task.getId(), task.getPlan().getId(), task.getPlan().getExamName(),
                task.getScheduledDate(), task.getSortOrder(), task.getType(), task.getTopic(), task.getTitle(),
                task.getDescription(), task.getMinutes(), task.getStatus(), task.getCompletedAt());
    }
}
