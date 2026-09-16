package com.recallai.dto;

import com.recallai.entity.KnowledgeLevel;
import com.recallai.entity.StudyPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StudyPlanSummaryResponse(
        Long id,
        String examName,
        LocalDate examDate,
        List<String> topics,
        KnowledgeLevel knowledgeLevel,
        int minutesPerDay,
        StudyPlanStatus status,
        boolean aiGenerated,
        Instant createdAt,
        StudyPlanProgress progress) {
}
