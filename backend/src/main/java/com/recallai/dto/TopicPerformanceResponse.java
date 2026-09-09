package com.recallai.dto;

import java.time.Instant;

/**
 * @param averageQuality       mean quality over every review of the topic
 * @param recentAverageQuality mean quality over the most recent reviews (the value the weak rule uses)
 * @param successRatePercent   share of reviews with quality >= 3
 */
public record TopicPerformanceResponse(
        String topic,
        long cardCount,
        long reviews,
        double averageQuality,
        double recentAverageQuality,
        int successRatePercent,
        Instant lastReviewedAt,
        boolean weak) {
}
