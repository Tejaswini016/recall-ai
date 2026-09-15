package com.recallai.service;

import com.recallai.dto.TopicCategory;

/**
 * The deterministic rule that turns a topic's combined accuracy into a category and a
 * recommended next action. Isolated so the thresholds are visible and unit-tested in one place.
 * Not a job for a language model: the same numbers must always give the same label.
 */
public final class TopicCategorizer {

    public static final int STRONG_MIN_PERCENT = 85;
    public static final int GOOD_MIN_PERCENT = 70;
    public static final int WEAK_MIN_PERCENT = 50;

    private TopicCategorizer() {
    }

    /**
     * @param attempts    card reviews plus quiz answers on the topic
     * @param accuracy    percentage of successful attempts, 0-100
     * @param minAttempts attempts needed before a verdict is given
     */
    public static TopicCategory categorize(long attempts, int accuracy, int minAttempts) {
        if (attempts < minAttempts) {
            return TopicCategory.UNRATED;
        }
        if (accuracy >= STRONG_MIN_PERCENT) {
            return TopicCategory.STRONG;
        }
        if (accuracy >= GOOD_MIN_PERCENT) {
            return TopicCategory.GOOD;
        }
        if (accuracy >= WEAK_MIN_PERCENT) {
            return TopicCategory.WEAK;
        }
        return TopicCategory.CRITICAL;
    }

    public static String recommendedAction(TopicCategory category) {
        return switch (category) {
            case CRITICAL -> "Relearn it: review your mistakes, then take a focused quiz";
            case WEAK -> "Practise it: run a targeted quiz and review its cards today";
            case GOOD -> "Keep going: review its cards when they come due";
            case STRONG -> "Maintain it: spaced reviews are enough";
            case UNRATED -> "Build a baseline: review or quiz it a few more times";
        };
    }

    /** Percentage helper shared with the insight service: rounds half up, 0 when nothing was attempted. */
    public static int percent(long successes, long attempts) {
        return attempts == 0 ? 0 : (int) Math.round(successes * 100.0 / attempts);
    }
}
