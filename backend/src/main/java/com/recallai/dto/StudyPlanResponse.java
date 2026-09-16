package com.recallai.dto;

import com.recallai.entity.KnowledgeLevel;
import com.recallai.entity.StudyPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param aiGenerated whether the summary and topic advice came from the model (validated) or from
 *                    the deterministic fallback; the schedule itself is always computed locally
 */
public record StudyPlanResponse(
        Long id,
        String examName,
        LocalDate examDate,
        List<String> topics,
        KnowledgeLevel knowledgeLevel,
        int minutesPerDay,
        List<Integer> preferredDays,
        StudyPlanStatus status,
        String summary,
        List<TopicAdvice> topicAdvice,
        boolean aiGenerated,
        Instant generatedAt,
        Instant createdAt,
        StudyPlanProgress progress,
        List<StudyPlanTaskResponse> tasks) {

    public record TopicAdvice(String topic, String advice) {
    }
}
