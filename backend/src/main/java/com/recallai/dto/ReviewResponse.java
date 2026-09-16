package com.recallai.dto;

import com.recallai.scheduler.DifficultyTier;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of grading one card.
 *
 * @param remainingDue cards still due today for this user after this review, so the
 *                     study screen can show "N cards remaining" without another call
 */
public record ReviewResponse(
        Long cardId,
        int quality,
        boolean successful,
        BigDecimal previousEaseFactor,
        BigDecimal newEaseFactor,
        int previousInterval,
        int newInterval,
        int repetitions,
        LocalDate nextDueDate,
        boolean mastered,
        long remainingDue,
        DifficultyTier previousDifficulty,
        DifficultyTier difficulty,
        boolean difficultyChanged,
        int successStreak,
        int sm2Interval) {
}
