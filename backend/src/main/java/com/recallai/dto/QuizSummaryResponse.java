package com.recallai.dto;

import java.time.Instant;

/**
 * @param bestScorePercent best result across the user's attempts, or null when never attempted
 */
public record QuizSummaryResponse(
        Long id,
        Long deckId,
        String deckName,
        String title,
        int questionCount,
        long attemptCount,
        Integer bestScorePercent,
        Instant createdAt) {
}
