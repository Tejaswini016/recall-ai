package com.recallai.dto;

import com.recallai.entity.QuizAttempt;
import java.time.Instant;

public record QuizAttemptSummaryResponse(
        Long id,
        int score,
        int totalQuestions,
        int percent,
        Instant completedAt,
        Integer durationSeconds) {

    public static QuizAttemptSummaryResponse from(QuizAttempt attempt) {
        return new QuizAttemptSummaryResponse(attempt.getId(), attempt.getScore(), attempt.getTotalQuestions(),
                attempt.percent(), attempt.getCompletedAt(), attempt.getDurationSeconds());
    }
}
