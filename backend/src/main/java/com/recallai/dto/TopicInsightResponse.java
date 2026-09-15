package com.recallai.dto;

import java.time.Instant;

/**
 * One topic's combined performance across flashcard reviews and quiz answers.
 *
 * @param attempts        card reviews plus quiz answers
 * @param mistakes        failed reviews (quality below 3) plus wrong quiz answers
 * @param accuracyPercent successful attempts as a percentage of all attempts
 * @param cardCount       cards carrying the topic (0 when only quiz questions use it)
 */
public record TopicInsightResponse(
        String topic,
        TopicCategory category,
        int accuracyPercent,
        long attempts,
        long mistakes,
        long cardCount,
        long cardReviews,
        long cardSuccesses,
        long quizAnswers,
        long quizCorrect,
        Instant lastStudiedAt,
        String recommendedAction) {
}
