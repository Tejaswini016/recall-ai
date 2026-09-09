package com.recallai.scheduler;

import java.math.BigDecimal;

/**
 * The three numbers SM-2 needs about a card. Deliberately independent of the JPA
 * entity so the algorithm can be tested and reasoned about without a database.
 *
 * @param easeFactor   how "easy" the card is; multiplies the interval on each success
 * @param intervalDays days between the last successful review and the next one
 * @param repetitions  consecutive successful reviews since the last failure
 */
public record Sm2State(BigDecimal easeFactor, int intervalDays, int repetitions) {

    /** Canonical SM-2 starting ease. */
    public static final BigDecimal INITIAL_EASE_FACTOR = new BigDecimal("2.50");

    /** SM-2 floor for the ease factor; below this, intervals would barely grow. */
    public static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");

    public Sm2State {
        if (easeFactor == null) {
            throw new IllegalArgumentException("easeFactor is required");
        }
        if (intervalDays < 0) {
            throw new IllegalArgumentException("intervalDays must be >= 0");
        }
        if (repetitions < 0) {
            throw new IllegalArgumentException("repetitions must be >= 0");
        }
    }

    /** State of a card that has never been reviewed. */
    public static Sm2State fresh() {
        return new Sm2State(INITIAL_EASE_FACTOR, 0, 0);
    }
}
