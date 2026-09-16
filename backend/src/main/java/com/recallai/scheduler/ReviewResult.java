package com.recallai.scheduler;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Output of one SM-2 calculation. Carries the previous values as well so the caller can
 * write a complete review-history row without re-reading the card.
 */
public record ReviewResult(
        int quality,
        boolean successful,
        BigDecimal previousEaseFactor,
        int previousInterval,
        int previousRepetitions,
        BigDecimal newEaseFactor,
        int newInterval,
        int newRepetitions,
        LocalDate nextDueDate) {

    public Sm2State newState() {
        return new Sm2State(newEaseFactor, newInterval, newRepetitions);
    }

    /** The same review with a rescaled interval (adaptive difficulty applies this after SM-2). */
    public ReviewResult withInterval(int interval, LocalDate dueDate) {
        return new ReviewResult(quality, successful, previousEaseFactor, previousInterval, previousRepetitions,
                newEaseFactor, interval, newRepetitions, dueDate);
    }
}
