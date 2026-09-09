package com.recallai.dto;

import java.time.LocalDate;

/**
 * Everything the dashboard's headline tiles need in one call.
 *
 * @param averageRecall      mean quality score (0–5) over all reviews, or null with no reviews
 * @param retentionRate      percentage of successful reviews in the last 30 days, or null
 * @param averageQuizPercent mean quiz score, or null with no attempts
 */
public record AnalyticsSummaryResponse(
        long dueToday,
        long reviewedToday,
        long totalCards,
        long cardsMastered,
        int masteredPercent,
        long totalDecks,
        long totalReviews,
        Double averageRecall,
        Integer retentionRate,
        int currentStreak,
        int longestStreak,
        LocalDate lastActiveDate,
        long quizzesTaken,
        Integer averageQuizPercent) {
}
