package com.recallai.dto;

import com.recallai.entity.ReviewHistory;
import com.recallai.scheduler.DifficultyTier;
import com.recallai.scheduler.Sm2Service;
import java.math.BigDecimal;
import java.time.Instant;

public record ReviewHistoryResponse(
        Long id,
        Long cardId,
        Long deckId,
        String deckName,
        String question,
        String topic,
        int quality,
        boolean successful,
        int previousInterval,
        int newInterval,
        BigDecimal previousEaseFactor,
        BigDecimal newEaseFactor,
        Instant reviewedAt,
        Integer responseMs,
        DifficultyTier difficultyAfter) {

    /** Requires card and deck to be loaded (the history query fetch-joins both). */
    public static ReviewHistoryResponse from(ReviewHistory history) {
        return new ReviewHistoryResponse(
                history.getId(),
                history.getCard().getId(),
                history.getCard().getDeck().getId(),
                history.getCard().getDeck().getName(),
                history.getCard().getQuestion(),
                history.getCard().getTopic(),
                history.getQualityScore(),
                history.getQualityScore() >= Sm2Service.PASSING_QUALITY,
                history.getPreviousInterval(),
                history.getNewInterval(),
                history.getPreviousEaseFactor(),
                history.getNewEaseFactor(),
                history.getReviewedAt(),
                history.getResponseMs(),
                history.getDifficultyAfter());
    }
}
