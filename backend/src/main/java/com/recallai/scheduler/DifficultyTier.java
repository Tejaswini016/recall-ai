package com.recallai.scheduler;

import java.math.BigDecimal;

/**
 * How hard a card currently is for this student, from easiest to hardest. Each tier carries the
 * factor applied to the interval SM-2 produces: easy and medium cards follow plain SM-2 (whose
 * ease factor already lengthens intervals for well-known cards), while hard and expert cards come
 * back sooner.
 */
public enum DifficultyTier {
    EASY(new BigDecimal("1.00")),
    MEDIUM(new BigDecimal("1.00")),
    HARD(new BigDecimal("0.85")),
    EXPERT(new BigDecimal("0.70"));

    private final BigDecimal intervalMultiplier;

    DifficultyTier(BigDecimal intervalMultiplier) {
        this.intervalMultiplier = intervalMultiplier;
    }

    public BigDecimal intervalMultiplier() {
        return intervalMultiplier;
    }

    /** One step harder; EXPERT stays EXPERT. */
    public DifficultyTier harder() {
        return ordinal() == values().length - 1 ? this : values()[ordinal() + 1];
    }

    /** One step easier; EASY stays EASY. */
    public DifficultyTier easier() {
        return ordinal() == 0 ? this : values()[ordinal() - 1];
    }
}
