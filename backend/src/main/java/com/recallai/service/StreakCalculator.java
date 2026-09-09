package com.recallai.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Turns the set of days a user reviewed on into streak numbers. Pure date arithmetic:
 * consecutive calendar days are checked explicitly rather than incrementing a counter.
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    public record StreakSummary(int currentStreak, int longestStreak, LocalDate lastActiveDate) {

        public static final StreakSummary NONE = new StreakSummary(0, 0, null);
    }

    /**
     * @param activeDays days with at least one review; order and duplicates do not matter
     * @param today      the reference day; a streak that ended yesterday still counts as
     *                   current because the user can extend it today
     */
    public static StreakSummary calculate(Collection<LocalDate> activeDays, LocalDate today) {
        if (activeDays == null || activeDays.isEmpty()) {
            return StreakSummary.NONE;
        }
        TreeSet<LocalDate> days = new TreeSet<>(activeDays);

        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = (previous != null && previous.plusDays(1).equals(day)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }

        LocalDate anchor = days.contains(today) ? today
                : days.contains(today.minusDays(1)) ? today.minusDays(1)
                : null;
        int current = 0;
        while (anchor != null && days.contains(anchor)) {
            current++;
            anchor = anchor.minusDays(1);
        }

        return new StreakSummary(current, longest, days.last());
    }
}
