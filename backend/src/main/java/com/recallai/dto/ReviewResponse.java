package com.recallai.dto;

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
        long remainingDue) {
}
