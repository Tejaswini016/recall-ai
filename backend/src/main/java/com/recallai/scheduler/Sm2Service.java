package com.recallai.scheduler;

import com.recallai.entity.Card;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * The SuperMemo SM-2 spaced-repetition algorithm (Wozniak, 1987), implemented as pure
 * arithmetic with no I/O. Given a card's current state and a recall quality 0–5 it
 * returns the next ease factor, interval, repetition count and due date.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>Quality below {@value #PASSING_QUALITY} is a lapse: repetitions reset to 0 and the
 *       card is scheduled for relearning {@value #RELEARN_INTERVAL_DAYS} day later. The ease
 *       factor is left unchanged, as in the original algorithm.</li>
 *   <li>On success the interval is 1 day for the first repetition, 6 days for the second,
 *       and {@code round(previousInterval × easeFactor)} afterwards, using the ease factor
 *       <em>before</em> this review's adjustment.</li>
 *   <li>On success the ease factor is then adjusted by
 *       {@code 0.1 − (5 − q) × (0.08 + (5 − q) × 0.02)} and floored at 1.30, so quality 5
 *       adds 0.10, quality 4 leaves it unchanged and quality 3 subtracts 0.14.</li>
 *   <li>The next due date is today plus the new interval.</li>
 * </ol>
 *
 * <p>Deterministic by design: scheduling is a business rule with a known-good algorithm,
 * not something to delegate to a language model.
 */
@Service
public class Sm2Service {

    public static final int MIN_QUALITY = 0;
    public static final int MAX_QUALITY = 5;
    /** Lowest quality that counts as a successful recall. */
    public static final int PASSING_QUALITY = 3;

    static final int FIRST_INTERVAL_DAYS = 1;
    static final int SECOND_INTERVAL_DAYS = 6;
    static final int RELEARN_INTERVAL_DAYS = 1;

    private static final int EASE_SCALE = 2;
    private static final BigDecimal EASE_BASE_BONUS = new BigDecimal("0.10");
    private static final BigDecimal EASE_LINEAR_PENALTY = new BigDecimal("0.08");
    private static final BigDecimal EASE_QUADRATIC_PENALTY = new BigDecimal("0.02");

    private final Clock clock;

    public Sm2Service(Clock clock) {
        this.clock = clock;
    }

    /** Convenience adapter: reads the state from a card and uses today's date. */
    public ReviewResult calculateNextReview(Card card, int quality) {
        Sm2State state = new Sm2State(card.getEaseFactor(), card.getIntervalDays(), card.getRepetitions());
        return schedule(state, quality, LocalDate.now(clock));
    }

    /** The algorithm itself. Pure: same inputs always give the same output. */
    public ReviewResult schedule(Sm2State current, int quality, LocalDate today) {
        validateQuality(quality);
        BigDecimal previousEase = clampEase(current.easeFactor());
        boolean successful = quality >= PASSING_QUALITY;

        int newInterval;
        int newRepetitions;
        BigDecimal newEase;
        if (successful) {
            newInterval = nextInterval(current.repetitions(), current.intervalDays(), previousEase);
            newRepetitions = current.repetitions() + 1;
            newEase = adjustEase(previousEase, quality);
        } else {
            newInterval = RELEARN_INTERVAL_DAYS;
            newRepetitions = 0;
            newEase = previousEase;
        }

        return new ReviewResult(
                quality,
                successful,
                previousEase,
                current.intervalDays(),
                current.repetitions(),
                newEase,
                newInterval,
                newRepetitions,
                today.plusDays(newInterval));
    }

    private static int nextInterval(int repetitions, int previousInterval, BigDecimal ease) {
        if (repetitions == 0) {
            return FIRST_INTERVAL_DAYS;
        }
        if (repetitions == 1) {
            return SECOND_INTERVAL_DAYS;
        }
        int grown = BigDecimal.valueOf(previousInterval).multiply(ease)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return Math.max(grown, previousInterval + 1);
    }

    /** EF' = EF + (0.1 − (5 − q) × (0.08 + (5 − q) × 0.02)), floored at the minimum. */
    private static BigDecimal adjustEase(BigDecimal ease, int quality) {
        BigDecimal shortfall = BigDecimal.valueOf(MAX_QUALITY - quality);
        BigDecimal penalty = shortfall.multiply(EASE_LINEAR_PENALTY.add(shortfall.multiply(EASE_QUADRATIC_PENALTY)));
        BigDecimal adjusted = ease.add(EASE_BASE_BONUS).subtract(penalty).setScale(EASE_SCALE, RoundingMode.HALF_UP);
        return clampEase(adjusted);
    }

    private static BigDecimal clampEase(BigDecimal ease) {
        BigDecimal scaled = ease.setScale(EASE_SCALE, RoundingMode.HALF_UP);
        return scaled.compareTo(Sm2State.MIN_EASE_FACTOR) < 0 ? Sm2State.MIN_EASE_FACTOR : scaled;
    }

    private static void validateQuality(int quality) {
        if (quality < MIN_QUALITY || quality > MAX_QUALITY) {
            throw new IllegalArgumentException(
                    "quality must be between " + MIN_QUALITY + " and " + MAX_QUALITY + " but was " + quality);
        }
    }
}
