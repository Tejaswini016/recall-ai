package com.recallai.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Adaptive difficulty, as a pure rule set beside SM-2 rather than inside it.
 *
 * <p>Tier movement per review:
 * <ul>
 *   <li>A lapse (quality below 3) moves the card one tier harder; a complete blank (quality 0)
 *       moves it two. The success streak resets.</li>
 *   <li>Three consecutive successes rated 4 or 5 that were not unusually slow move the card one
 *       tier easier; the streak then restarts so the next promotion needs three more.</li>
 *   <li>A success rated 3 (correct with effort), or a slow one, keeps the tier.</li>
 * </ul>
 *
 * <p>Response time is the time from seeing the question to revealing the answer, reported by the
 * client when it has it. A review is "slow" when it takes more than twice the card's running
 * average. The average is an exponential moving average so one outlier does not dominate.
 *
 * <p>The tier then scales the interval SM-2 produced, but only from the third repetition on, so
 * SM-2's fixed 1-day and 6-day seeds and its lapse handling are never altered.
 */
@Service
public class AdaptiveDifficultyService {

    public static final int PROMOTION_STREAK = 3;
    public static final int PROMOTION_MIN_QUALITY = 4;
    /** Reviews slower than this multiple of the running average do not count towards promotion. */
    static final int SLOW_FACTOR = 2;
    /** Weight of the newest response time in the running average. */
    private static final BigDecimal EMA_NEW_WEIGHT = new BigDecimal("0.30");
    /** SM-2 repetitions from which the multiplier applies (the first two intervals are fixed seeds). */
    static final int MIN_REPETITIONS_FOR_MULTIPLIER = 3;

    /** What the rule needs to know about the card before the review. */
    public record State(DifficultyTier tier, int successStreak, int lapseCount, int totalReviews,
                        Integer avgResponseMs) {
        public static State fresh() {
            return new State(DifficultyTier.MEDIUM, 0, 0, 0, null);
        }
    }

    /** The card's counters after the review plus whether the tier moved. */
    public record Outcome(DifficultyTier previousTier, DifficultyTier tier, int successStreak, int lapseCount,
                          int totalReviews, Integer avgResponseMs, boolean slow) {
        public boolean tierChanged() {
            return previousTier != tier;
        }
    }

    public Outcome evaluate(State state, int quality, Integer responseMs) {
        boolean slow = responseMs != null && state.avgResponseMs() != null
                && responseMs > (long) SLOW_FACTOR * state.avgResponseMs();
        Integer average = nextAverage(state.avgResponseMs(), responseMs);
        int totalReviews = state.totalReviews() + 1;
        DifficultyTier tier = state.tier();

        if (quality < Sm2Service.PASSING_QUALITY) {
            tier = quality == 0 ? tier.harder().harder() : tier.harder();
            return new Outcome(state.tier(), tier, 0, state.lapseCount() + 1, totalReviews, average, slow);
        }

        int streak = state.successStreak() + 1;
        if (quality >= PROMOTION_MIN_QUALITY && !slow && streak >= PROMOTION_STREAK) {
            tier = tier.easier();
            streak = 0;
        }
        return new Outcome(state.tier(), tier, streak, state.lapseCount(), totalReviews, average, slow);
    }

    /**
     * Scales an SM-2 interval by the tier's factor. Lapses (interval 1) and the two seed intervals
     * pass through unchanged; the result is never shorter than one day.
     */
    public int adjustInterval(int sm2Interval, int newRepetitions, DifficultyTier tier) {
        if (newRepetitions < MIN_REPETITIONS_FOR_MULTIPLIER
                || tier.intervalMultiplier().compareTo(BigDecimal.ONE) == 0) {
            return sm2Interval;
        }
        int scaled = BigDecimal.valueOf(sm2Interval).multiply(tier.intervalMultiplier())
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
        return Math.max(1, scaled);
    }

    static Integer nextAverage(Integer previous, Integer responseMs) {
        if (responseMs == null) {
            return previous;
        }
        if (previous == null) {
            return responseMs;
        }
        BigDecimal blended = BigDecimal.valueOf(previous).multiply(BigDecimal.ONE.subtract(EMA_NEW_WEIGHT))
                .add(BigDecimal.valueOf(responseMs).multiply(EMA_NEW_WEIGHT));
        return blended.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
